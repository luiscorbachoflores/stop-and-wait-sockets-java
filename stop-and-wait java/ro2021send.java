import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
public class ro2021send {
	
	public static void main (String[] args) {
		
		System.out.println(args.length);
		if(args.length != 5){
			System.out.println("Sintaxis correcta: $ java ro2021send input_file dest_IP dest_port emulator_ip emulator_port");
			System.exit(-1);
		
		}
		
		String fichero = args[0];
		String IP_destino = args[1];
		int puerto_destino = Integer.parseInt(args[2]);	
		String IP_emulator = args[3];
		int puerto_emulator = Integer.parseInt(args[4]);
		
		String direccionDestino = IP_destino+":"+args[2];
		
		RandomAccessFile archivo = null;
		TreeMap <Integer,byte[]> archivo_dividido = new TreeMap <Integer,byte[]>();
		int mtu = 1472;
		int final_letter_length = mtu;
		int cabecera = 16;
		
		try{
			archivo = new RandomAccessFile(args[0],"r");
			long longitud = archivo.length();
			int num_paquete = 1;
			int bytes_datos = mtu-cabecera;
			
			do{
				byte[] entrada = new byte[bytes_datos];
				final_letter_length = archivo.read(entrada);
				archivo_dividido.put( num_paquete , entrada );
				longitud = longitud-bytes_datos;
				num_paquete ++;
			}while(longitud>0);
		
		}catch(Exception e0){
		
			System.out.println(e0);
		
		}
		
		try{
			
			DatagramChannel channelUDP = DatagramChannel.open();
			channelUDP.socket().bind( new InetSocketAddress(0) );
			Selector selector = Selector.open();
			channelUDP.configureBlocking( false );
			SelectionKey channelKey=channelUDP.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
			
			ByteBuffer buffer = ByteBuffer.allocate(mtu);  
			
			buffer.position(0);
			
			for(int i = 0; i < 4 ; i++){
				int entrada = Integer.parseInt(IP_destino.split("[.]")[i]);
				buffer.put((byte)entrada);
			}
			
			buffer.position(4);
			buffer.putShort((short) puerto_destino);
			
			int RTO = 50;
			int rtt_media = 0;
			int rtt_desviacion = 0;
			double alfa = 0.125;
			double beta = 0.25;
			int contador_timeouts=0;
			
			int numsec_paquete = 1;
			short flagMF=1;
			short salir=1;
			int mensajes_enviados=0;
			int contador_envio_ultimo_paquete=0;
			
			while(salir==1){
					int selector_condicion=selector.select(RTO+2);
					
					
					
					if(selector_condicion == 0){
						contador_timeouts++;
						channelKey.interestOps(SelectionKey.OP_WRITE);
						continue;
					}

					Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
					
					while(keyIterator.hasNext()){
					
						SelectionKey key = keyIterator.next();
					
						if(key.isWritable()){
						
							buffer.position(6);
							buffer.putShort((short)numsec_paquete);
					
							buffer.position(8);
							if(numsec_paquete<archivo_dividido.lastKey()) flagMF = 1;
							else flagMF = 0;
							buffer.putShort(flagMF);

							buffer.position(10);
							long startTime = System.currentTimeMillis();
							buffer.putInt ( (int) startTime);
				
							buffer.position(14);
							if(flagMF == 0) buffer.putShort( (short) final_letter_length);
							else if (flagMF != 0)buffer.putShort( (short) mtu);
				
							buffer.position(16);
							buffer.put(archivo_dividido.get(numsec_paquete));

							buffer.position(0);
				
							InetAddress direccion_emulator = InetAddress.getByName(IP_emulator);
							InetSocketAddress socketEmulator = new InetSocketAddress(direccion_emulator,puerto_emulator);
							if(numsec_paquete==archivo_dividido.lastKey()){
								contador_envio_ultimo_paquete++;
								if(contador_envio_ultimo_paquete>3){
									salir=0;
									}
							}
							
							channelUDP.send(buffer,socketEmulator);
							mensajes_enviados++;
							
							key.interestOps(SelectionKey.OP_READ);
						
						}else if(key.isReadable()){
						
							ByteBuffer ack_buffer = ByteBuffer.allocate(20);
							
							channelUDP.receive(ack_buffer); 		//llega el bytebuffer
							
							ack_buffer.position(8);
							
							salir=ack_buffer.getShort();
				
							ack_buffer.position(6);
							int num_paquete_aceptado = (int) ack_buffer.getShort();
				
							if ((num_paquete_aceptado == numsec_paquete)){
								ack_buffer.position(10);
								int tiempo_total=((int)System.currentTimeMillis()- ack_buffer.getInt());
								if(numsec_paquete==1){
									rtt_media=tiempo_total;
									rtt_desviacion=tiempo_total/2;
								}else{						
									rtt_media=(int) ( (1-alfa) * rtt_media + alfa * tiempo_total );
									rtt_desviacion=(int) ( (1-beta) * rtt_desviacion + beta * Math.abs(rtt_media - tiempo_total) );
								}
								RTO=rtt_media+4*rtt_desviacion;
								numsec_paquete++;
								key.interestOps(SelectionKey.OP_WRITE);
								
							}else if(num_paquete_aceptado == archivo_dividido.lastKey()){
								salir=0;
							}else{
								ack_buffer.position(10);
								int tiempo_total=((int)System.currentTimeMillis()-ack_buffer.getInt());
								if(numsec_paquete==1){
									rtt_media=tiempo_total;
									rtt_desviacion=tiempo_total/2;
								}else{						
									rtt_media=(int) ( (1-alfa) * rtt_media + alfa * tiempo_total );
									rtt_desviacion=(int) ( (1-beta) * rtt_desviacion + beta * Math.abs(rtt_media - tiempo_total) );
								}
								RTO=rtt_media+4*rtt_desviacion;
								key.interestOps(SelectionKey.OP_READ);
							}	
						}
						keyIterator.remove();
					}
				}	
		}catch(IOException e2){
			System.out.println(e2.toString());
			System.exit(-1);
		}catch(Exception e3){
				System.out.println(e3.toString());
				e3.printStackTrace();
				System.exit(-1);
		
			}
	}
}
