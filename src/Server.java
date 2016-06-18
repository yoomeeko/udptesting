import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.zip.CRC32;
public class Server {
	final static int MAXBUFFER = 508;
	
	public static InetAddress remoteaddr;
	public InetAddress myinetaddr;
	public static int remoteport=0, myport=0; 
	
	static RcvThread rcvthread; 
	public static DatagramSocket socket;
	static Signaling p= new Signaling(); // Object를 생성해서 argument로 패싱해야 waiting/notify가 됨
	static Timeout tclick; // Timeout Interface
	
	public static int ns=0;
	public static int nr=0;
	private static String ID;
	//Server mode : args[0] - port, args[1] - ID
	//Client mode : args[0] - addr, args[1] - port, args[2] - ID
	public static void main(String args[]){
		
		/******************************************************/
		/***************Port/Addr Initialization***************/
		/******************************************************/
		if(args.length == 3){
			ID = args[2];
			remoteport = Integer.parseInt(args[1]);
			try {
				remoteaddr = InetAddress.getByName(args[0]);
			} catch (UnknownHostException e) {
				System.out.println("Error on port"+remoteport);e.printStackTrace();
			}
		} else if(args.length == 2){
		// server mode without sending first: wait for an incoming message
			ID = args[1];
			myport = Integer.parseInt(args[0]); //server mode
		} else {
			System.out.println("사용법: java UDPChatting localhost port or port");
			System.exit(0);
		}

				
		try{
			if(myport==0) {socket = new DatagramSocket();}
			else 		 {socket = new DatagramSocket(myport);}
			System.out.println("Datagram my address on "+InetAddress.getLocalHost().getHostAddress()+"my port "+socket.getLocalPort());
			tclick = new Timeout();
			rcvthread = new RcvThread(socket, p, ID);
			rcvthread.start();			
			byte buffer[] = new byte[MAXBUFFER];			
			DatagramPacket send_packet;
			
			
			/***************************************************/
			/**************Initialize Connection****************/
			/***************************************************/
			if(args.length == 3)//client
			{	
				//send U-frame
				int i;
				buffer = makeUframe(ID, "client");
				send_packet = new DatagramPacket(buffer, buffer.length, remoteaddr, remoteport);// 송신용 데이터그램 패킷
				for(i=0; i<10;i++) { // 10 times retransmission
					socket.send (send_packet);//send U-frame
					tclick.Timeoutset(i, 1000, p);// Timeout Start
					p.waitingACK(); /* ACKED */		
					//System.out.println("Wakeup!");
					if(Signaling.ACKNOTIFY) {tclick.Timeoutcancel(i); break;} //timeout 되기전 ACK 도착
						// true: ACK,  false: Timeout
					else System.out.println("Retransmission " + buffer.toString());
				}			
				if(i==10) return; //Server didn't accept the client. It terminated.	
			} else if(args.length == 2)//server
			{	
				p.waitingACK();
				// true: ACK,  false: Timeout
			}

			
			/***************************************************/
			/*******************Chatting mode*******************/
			/***************************************************/			
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			while (true) {
				// 키보드 입력 읽기
				String information = br.readLine();
				if (information.length() == 0){ // no char carriage return 
					System.out.println("grace out call");
					break;
				}					
				//I-frame 생성
				buffer = makeIframe(ID, nr, information);
				int i=1;
				while(i<Server.MAXBUFFER+1){
					if(buffer[i] == 0x7E) break;
					i++;
				}
				buffer = Arrays.copyOfRange(buffer, 0,i+1);
			/*	for(i=3; i < buffer.length-5; i++) {
					System.out.print((char)buffer[i]);

				}
				System.out.println();*/
				// 데이터 송신
				for( i=0; i<10;i++) { // 10 times retransmission
					send_packet = new DatagramPacket(buffer, buffer.length, remoteaddr, remoteport);
					socket.send (send_packet);
					tclick.Timeoutset(i, 1000, p);// Timeout Start
					p.waitingACK(); /* ACKED */
					if(Signaling.ACKNOTIFY) {
						ns = (ns+1)%2;
						tclick.Timeoutcancel(i);
						break;} //timeout 되기 전에 ACK 도착
					// true: ACK,  false: Timeout
					else System.out.println("Retransmission : "+information);
				}
			}
		}catch(IOException e) {
			System.out.println(e);
		}
	}
	
	
	
	//각각 프레임을 만들어주는 함수
	static byte[] makeSframe(String ID)
	{
		CompactBitSet compbitset = new CompactBitSet();
		byte flag = (byte) 0x7E;
		compbitset.append(flag);
		byte id = (byte) Integer.parseInt(ID);
		compbitset.append(id);
		byte control = makeScontrol();
		compbitset.append(control);
		byte[] crccode = new byte[4];
		crccode = getCRC(compbitset.toByteArray(),compbitset.toByteArray().length-1);
		compbitset.append(crccode);
		compbitset.append(flag);
	//	System.out.println(compbitset.toString());
		return compbitset.toByteArray();
	}
	
	static byte[] makeIframe(String ID, int rn, String information) {
		// TODO Auto-generated method stub
		CompactBitSet compbitset = new CompactBitSet();
		byte flag = (byte) 0x7E;
		compbitset.append(flag);
		byte id = (byte) Integer.parseInt(ID);
		compbitset.append(id);
		byte control = makeIcontrol();
		compbitset.append(control);
		byte[] info = information.getBytes();
		compbitset.append(info);
		byte[] crccode = new byte[4];
		crccode = getCRC(compbitset.toByteArray(),compbitset.toByteArray().length-1);
		compbitset.append(crccode);
		compbitset.append(flag);
	//	System.out.println(compbitset.toString());
		
		return compbitset.toByteArray();
	}
	static byte[] makeUframe(String ID, String mode)
	{
		CompactBitSet compbitset = new CompactBitSet();
		byte flag = (byte) 0x7E;
		compbitset.append(flag);
		byte id = (byte) Integer.parseInt(ID);	
		compbitset.append(id);
		byte control;
		if(mode.equals("client")) control = (byte) 0xC1; 
		else control = (byte) 0xD1;
		compbitset.append(control);
		byte[] information = new byte[500];
		for(int i=0; i<500; i++) information[i] = 0x00;
		compbitset.append(information);
		byte[] crccode = new byte[4];
		crccode = getCRC(compbitset.toByteArray(), compbitset.toByteArray().length-1);
		compbitset.append(crccode);
		compbitset.append(flag);
	//	System.out.println(compbitset.toString());
		return compbitset.toByteArray();
	}
	static byte[] getCRC(byte[] frame, int length)
	{
		byte[] tempCRC = new byte[4];
		
		CRC32 crc32 = new CRC32();
		crc32.update(frame, 1, length);
		long temp = crc32.getValue();
		System.out.println(temp);//CRC value 출력
		tempCRC[3] = (byte)(int)(temp & 255L);
		tempCRC[2] = (byte)(int)(temp >>> 8 & 255L);
		tempCRC[1] = (byte)(int)(temp >>> 16 & 255L);
		tempCRC[0] = (byte)(int)(temp >>> 24 & 255L);
		return tempCRC;
	}
	private static byte makeScontrol() {
		// TODO Auto-generated method stub
		byte control = (byte)0x80;
		if(nr > 2) nr = nr %2;
		control = (byte)  (control | nr);
		return control;
	}
	private static byte makeIcontrol() {
		// TODO Auto-generated method stub
		byte control = 0x00;
		if(ns > 2) ns = ns%2;
		if(nr > 2) nr = nr%2;
		ns <<= 4;
		control = (byte) (control | ns | nr);
		return control;
	}
}
