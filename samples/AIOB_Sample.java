
public class AIOB_Sample 
{
    int[] fa = new int[4];
    int[] fb;
    
    public void testOutOfBounds()
    {
        int[] a = new int[4];
        
        a[4] = 2;
        fa[4] = 2;
    }
    
    public void testUnallocated()
    {
        int[] b = null;
        
        b[4] = 4;
        fb[4] = 4;
    }
}
