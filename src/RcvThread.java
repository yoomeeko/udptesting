import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.zip.CRC32;


class RcvThread extends Thread {
	DatagramSocket socket;
	boolean	sem=true;
	DatagramPacket rcv_packet;// 수신용 데이터그램 패킷
	DatagramPacket send_packet;
	Signaling p;
	String ID;
	boolean error = false;
	int bufferlength;
	RcvThread (DatagramSocket s, Signaling pp, String ID) {
		socket = s;
		p=pp;		
		this.ID = ID;
	}	
	
	public void run() {
		byte[] bufftemp = new byte[Server.MAXBUFFER+1];
		byte[] buff = null;
		String s;
		System.out.println("hello");
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
			int i=1;
			while(i<Server.MAXBUFFER+1){
				if(buff[i] == 0x7E) break;
				i++;
			}
			bufferlength = i;
			buff = Arrays.copyOfRange(buff, 0,bufferlength);
			error = Error(buff);
            if(error) continue;
            if(IsSframe(buff) && getNRFromFrame(buff) ==(Server.nr+1)%2) p.ACKnotifying();
			if(IsIframe(buff)){
				for(int j=3; j<buff.length-5; j++){
					System.out.print((char)buff[j]);
				}
				System.out.println();
			}
			makeFrameAndSendingIfNeed(buff);
		}
		System.out.println("grace out");
	}
	//프레임을 만드는데 만약 필요하면 응답 프레임을 전송하는 함수
	private byte[] makeFrameAndSendingIfNeed(byte[] buff) {
		// TODO Auto-generated method stub
        if(IsUframe(buff)){
            if(IsClient(buff)){//서버가 클라이언트로부터 연결 요청을 받음
                //답장 uframe 전송
                buff = Server.makeUframe(ID, "Server");
                send_packet = new DatagramPacket(buff, buff.length, Server.remoteaddr, Server.remoteport);
                try {
                    socket.send(send_packet);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            else {
                //그냥 에크 노티파이
                p.ACKnotifying();
                System.out.println("서버와 연결");
            }
        }
        if(IsIframe(buff)) {
            if(getNRFromFrame(buff)!=Server.nr %2) continue;
			Server.nr = (Server.nr+1)%2;
			buff = Server.makeSframe(ID);
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
	
	
	
	
	//각각 무슨 프레임인지를 구분하는 프레임
	private boolean IsIframe(byte[] buff) {
		// TODO Auto-generated method stub
		byte control = buff[2];
		if(control == (byte) 0x01 ||control == (byte) 0x12||control == (byte) 0x23||control == (byte) 0x34
				||control == (byte) 0x45||control == (byte) 0x56||control == (byte) 0x67||control == (byte) 0x70) return true;
		else return false;
	}
	private boolean IsSframe(byte[] buff) {
		// TODO Auto-generated method stub
		byte control = buff[2];
		if((control >= (byte)0x90 && control <=(byte)0x97) || (control >= (byte)0x80 && control <=(byte)0x87) ) return true;
		else return false;
	}
	private boolean IsUframe(byte[] buff) {
		// TODO Auto-generated method stub
		byte control = buff[2];
		if(control == (byte) 0xC1 || control == (byte) 0xD1)
			return true;
		else return false;
	}
	
	
	
	//client인지를 구분하는 함수
	private boolean IsClient(byte[] buff) {
		// TODO Auto-generated method stub
		byte control = buff[2];
		if(control == (byte) 0xC1) return true;
		else return false;
	}
	
	
	
	
	//에러를 검출하는 함수
	private boolean Error(byte[] buff) {
		// TODO Auto-generated method stub
		boolean result = true;
        byte[] tmp = new byte[buff.length -6];
        for(int i=0; i<buff.length-6; ++i)
            tmp[i] = buff[buff.length-5+i];
        byte[] tmp_crc = new byte[4];
        byte[] crc = new byte[4];
        for(int i=0; i<4; i++){
            tmp_crc[i] = buff[buff.length - 5 + i];
        }
        crc = Server.getCRC(tmp, tmp.length);
        for(int i=0; i<4; i++)
        {
            if(tmp_crc[i] != crc[i])
                result = false;
        }
		return false;
	}
    public int getNRFromFrame(byte[] buff){
        int nr = 0;
        byte[] BinaryFrame = buff;
        if(IsIframe(buff) || IsSframe(buff)) nr = BinaryFrame[2] & 0x07;
        else{
            try{
                throw new Exception("Incompatible type of frame.");
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
        return nr;
    }
    public int getNSFromFrame(byte[] buff){
        int ns = 0;
        byte[] BinaryFrame = buff;
        if(IsIframe(buff)) ns = (BinaryFrame[2] >>> 4) & 0x07;
        else {
            try {
                throw new Exception("Incompatible type of frame to get N(S).");
            } catch(Exception e){
                e.printStackTrace();
            }
        }
        return ns;
    }
	public void graceout(){
		sem=false;
	}	
} // end ReceiverThread class
