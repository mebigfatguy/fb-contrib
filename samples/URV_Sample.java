import java.util.HashSet;
import java.util.TreeSet;

@SuppressWarnings("all")
public class URV_Sample extends URV_Super
{
	public Object getASet(boolean b)
	{
		if (b)
			return new HashSet();
		else
			return new TreeSet();
	}
	
	public Object getInfo(boolean b)
	{
		if (b)
			return new String[4];
		else
			return "";
	}
	
	@Override
	public Object getInheritedInfo(boolean b)
	{
		if (b)
			return new Integer(1);
		else
			return new Float(1.0);
	}
}

class URV_Super
{
	public Object getInheritedInfo(boolean b)
	{
		return null;
	}
}
