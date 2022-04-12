import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.DatagramChannel;

public class ro2021recv {
	
	public static void main (String[] args) {
		
		if(args.length != 2){
			
			System.out.println("Sintaxis correcta: $ java ro2021recv output_file listen_port");
			System.exit(-1);
		}
		
		int puerto = Integer.parseInt(args[1]);
		
		int mtu = 1472;
		int cabecera = 16;
		RandomAccessFile archivo = null;
		int numero_paquetes_recibidos=0;
		int numero_paquetes_asentidos=0;
		
		try{
			archivo = new RandomAccessFile(args[0],"rw");
				
		}catch(Exception e0){
		
			System.out.println(e0);
			System.exit(-1);
		
		}
		
		try{
			long start = System.currentTimeMillis();
			int flagMF = 1;
			int num_paquete = 1;
			byte [] buffer = new byte[mtu];
			DatagramChannel serverUDP = DatagramChannel.open();
			serverUDP.socket().bind(new InetSocketAddress(puerto));
			
			while(flagMF == 1){
				
				ByteBuffer peticion = ByteBuffer.allocate(mtu);
				
				String direccion_emulator = serverUDP.receive(peticion).toString().replaceFirst("/","");
				peticion.position(6);
				int num_paquete_recibido = (int) peticion.getShort();
				numero_paquetes_recibidos++;
				byte[] cabecera_bytes = new byte[cabecera];
				peticion.position(0);
				peticion.get(cabecera_bytes);
				
				String IP_emulator = direccion_emulator.split(":")[0];
				int puerto_emulator = Integer.parseInt(direccion_emulator.split(":")[1]);
				
				InetSocketAddress socket_emulator = new InetSocketAddress(InetAddress.getByName(IP_emulator),puerto_emulator);
				
				ByteBuffer buffer_retorno = ByteBuffer.allocate(20);
				buffer_retorno.position(0);
				buffer_retorno.put(cabecera_bytes);
				
				buffer_retorno.position(0);
				
				if(num_paquete_recibido < num_paquete){
					
					serverUDP.send(buffer_retorno,socket_emulator);
					buffer_retorno.clear();
					peticion.clear();
					continue;
				
				}
				
				peticion.position(8);
				flagMF = (int)peticion.getShort();
				
				byte[] datos_bytes = new byte[mtu-cabecera];
				peticion.position(cabecera);
				peticion.get(datos_bytes);
				
				peticion.position(14);
				int longitudMensaje = (int)peticion.getShort();
				if(flagMF == 0){
					
					byte[] smallerData = new byte[longitudMensaje];
					System.arraycopy(datos_bytes, 0, smallerData, 0, longitudMensaje);
					datos_bytes = smallerData;
					
					archivo.write(datos_bytes);
					archivo.close();
				}else archivo.write(datos_bytes);
				
				
				num_paquete = num_paquete+1;
				
				buffer_retorno.position(0);
				serverUDP.send(buffer_retorno,socket_emulator);
				numero_paquetes_asentidos++;
				buffer_retorno.clear();
				peticion.clear();
				
			}
		}catch(Exception e){
			
			System.out.println(e);
			e.printStackTrace();
			System.exit(-1);
		
		}
			
	}
}



