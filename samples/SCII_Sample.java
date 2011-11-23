import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class SCII_Sample extends OverEndulgentParent implements MouseListener
{
	public void mouseClicked(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}	
}

class OverEndulgentParent 
{
	public void mousePressed(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	public void mouseReleased(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	public void mouseEntered(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	public void mouseExited(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}
    
    interface A
    {
        public void a();
        
        public void b();
        
        public void c();
    }
    
    interface B extends A
    {
        public void b();
    }
    
    interface C extends B
    {
    	public void c();
    }
    
    class AA implements A
    {
        public void a() {}
        
        public void b() {}
        
        public void c() {}
    }
    
    class BB extends AA implements B
    {
        
    }
    
    class CC extends BB implements C
    {
    	
    }
}
