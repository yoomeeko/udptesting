/**
 * Simple demo that uses java.util.Timer to schedule a task 
 * to execute once 1000 mili seconds have passed.
 */
import java.util.Timer;
import java.util.TimerTask;

/*  Timeout ���� �۾� (timer & TimeoutTask ����, TimeoutTask ���� �� ����) 
 *  �۽� �����尡 ���� ���� ���� ��� wait �ϸ鼭 �۽� ��Ŷ�� ���� ������ ��ٸ�.
 *  TimeoutTask ����: ���� ��Ŷ�� seq ��ȣ�� ���� timeout �ð��� �����ؼ� Timer�� ������
 *  TimeoutTask ����: Ack ���� ��Ŷ�� seq ��ȣ�� ���� timeoutTask�� Timer���� ������ 
 */
public class Timeout {
    Timer timer= new Timer();
    TimeoutTask[] myTimerTask = new TimeoutTask[16]; //MAXSIZE
    Signaling pp;
    public int timeoutlimit=0;
    boolean DEBUG=false;
    public void Timeoutset (int i, int milliseconds, Signaling p) {
    	// TimeoutTask ����: ���� ��Ŷ�� seq ��ȣ�� ���� timeout �ð��� �����ؼ� Timer�� ������
    	pp=p;
    	myTimerTask[i]=new TimeoutTask(i);
        timer.schedule(myTimerTask[i], milliseconds);
	}
    public void Timeoutcancel (int i) {
    	// TimeoutTask ����: Ack ���� ��Ŷ�� seq ��ȣ�� ���� timeoutTask�� Timer���� ������
    	int k=i;
    	if(DEBUG) System.out.println("Time's cancealed! no="+k);
        myTimerTask[k].cancel();
	}

    class TimeoutTask extends TimerTask {
    	int jj;
    	TimeoutTask(int j) {    		
    		jj=j;
    	}
    	public void run() {
            if(DEBUG) System.out.println("Time's up! "+(timeoutlimit));
            pp.Timeoutnotifying();
            this.cancel(); //Terminate the timerTask thread
        }
    }
}