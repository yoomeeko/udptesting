import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.zip.CRC32;


class RcvThread extends Thread {
	DatagramSocket socket;
	boolean	sem=true;
	DatagramPacket rcv_packet;// 수신용 데이터그램 패킷
	DatagramPacket send_packet;
	Signaling p;
	String ID;
	boolean error = false;
	RcvThread (DatagramSocket s, Signaling pp, String ID) {
		socket = s;
		p=pp;		
		this.ID = ID;
	}	
	
	public void run() {
		byte[] bufftemp = new byte[Server.MAXBUFFER];
		byte[] buff = null;
		String s;
		rcv_packet = new DatagramPacket(bufftemp, bufftemp.length);
		while (sem) {
			try {
		       socket.receive(rcv_packet);  
		       Server.remoteport = rcv_packet.getPort();// 임의의 소켓에 대한 응답을 위해
		       Server.remoteaddr = rcv_packet.getAddress();// 임의의 소켓에 대한 응답을 위해
		       CompactBitSet compbitset = new CompactBitSet();
		       compbitset.append(bufftemp);
		       buff = compbitset.toByteArray();
		       System.out.println(compbitset.toString());
			} catch(IOException e) {
				System.out.println("Thread exception "+e);
			}
			error = Error(buff);
			
			if((IsUframe(buff)||IsSframe(buff))&&error) continue;
			
			makeFrameAndSendingIfNeed(buff);


		}
		
		System.out.println("grace out");
	}
	private byte[] makeFrameAndSendingIfNeed(byte[] buff) {
		// TODO Auto-generated method stub
		if(IsUframe(buff)&&IsClient(buff)){
			buff = Server.makeUframe(ID, "Server");
			send_packet = new DatagramPacket(buff, buff.length, Server.remoteaddr, Server.remoteport);
			try {
				socket.send(send_packet);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			p.ACKnotifying();/* ACKED */
		}
		else if(IsSframe(buff)){
			p.ACKnotifying();/* ACKED */
		}
		else if(IsIframe(buff)){
			Server.rn = (Server.rn+1)%8;
			buff = Server.makeSframe(ID, error);
			send_packet = new DatagramPacket(buff, buff.length, Server.remoteaddr, Server.remoteport);
			try {
				socket.send(send_packet);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		return buff;
	}

	private boolean IsIframe(byte[] buff) {
		// TODO Auto-generated method stub
		byte control = buff[2];
		if(control >= (byte) 0x80 && control <= (byte) 0xBF) return true;
		else return false;
	}
	private boolean IsSframe(byte[] buff) {
		// TODO Auto-generated method stub
		byte control = buff[2];
		if(control >= (byte) 0x00 && control <= (byte) 0x7F) return true;
		else return false;
	}

	private boolean IsUframe(byte[] buff) {
		// TODO Auto-generated method stub
		byte control = buff[2];
		if(control == (byte) 0xC1 || control == (byte) 0xD1) return true;
		else return false;
	}
	private boolean IsClient(byte[] buff) {
		// TODO Auto-generated method stub
		byte control = buff[2];
		if(control == (byte) 0xC1) return true;
		else return false;
	}
	private boolean Error(byte[] buff) {
		// TODO Auto-generated method stub
		//1. flag 체크 buff[0]
		System.out.println(buff.length);
		if(buff[0] != 126) return true;
		
		if(buff[buff.length-1] != (byte) 126) return true;
		//2. CRC 체크 
		System.out.println(buff.length);
		byte[] crccode = new byte[4];
		crccode = Server.getCRC(buff, buff.length-6);
		if(crccode[0] != buff[buff.length-5] || crccode[1]!=buff[buff.length-4] || 
				crccode[2]!=buff[buff.length-3]||crccode[3]!=buff[buff.length-2])
			return true;
		System.out.println("hello");
		byte rntemp;
		if(IsSframe(buff)){
		//3. control 체크
			rntemp = (byte) (128+16+Server.rn);
			if(buff[2] != rntemp) return true;
			rntemp = (byte) (128 +Server.rn);
			if(buff[2] != rntemp) return true; 
		}
		if(IsIframe(buff)){
			
			rntemp = (byte)(Server.rn*16 + (Server.rn+1)%8);		
			if(buff[2] != rntemp) return true;
		}
		return false;
	}

	public void graceout(){
		sem=false;
	}	
} // end ReceiverThread class
