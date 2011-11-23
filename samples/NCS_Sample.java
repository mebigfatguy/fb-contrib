import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;


@SuppressWarnings("all")
public class NCS_Sample implements Serializable
{
	private void writeObject(ObjectOutputStream oos) throws IOException
	{
		oos.defaultWriteObject();
	}
	
	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException
	{
		ois.defaultReadObject();
	}
}
