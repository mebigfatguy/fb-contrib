import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;


public class HCP_Sample {

	private CloseableHttpClient client = HttpClients.createDefault();
	
	public void forgotToResetGet() throws URISyntaxException {
		//tag
		HttpGet httpGet = new HttpGet(new URI("http://www.example.com"));
		try
		{
			StringEntity content = new StringEntity("Some random content");
			content.setContentType("application/json");
			try(CloseableHttpResponse response = client.execute(httpGet);)
			{
				System.out.println("response: " +response);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public void forgotToResetPut() throws URISyntaxException {
		//tag
		HttpPut httpPut = new HttpPut(new URI("http://www.example.com"));
		try
		{
			StringEntity content = new StringEntity("Some random content");
			content.setContentType("application/json");

			httpPut.setEntity(content);
			try(CloseableHttpResponse response = client.execute(httpPut);)
			{
				System.out.println("response: " +response);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public void resetGet1() throws URISyntaxException {
		//no tag
		HttpGet httpGet = new HttpGet(new URI("http://www.example.com"));
		try
		{
			StringEntity content = new StringEntity("Some random content");
			content.setContentType("application/json");
			try(CloseableHttpResponse response = client.execute(httpGet);)
			{
				System.out.println("response: " +response);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally {
			httpGet.reset();
		}
	}
	
	public void resetGet2() throws URISyntaxException {
		//no tag
		HttpGet httpGet = new HttpGet(new URI("http://www.example.com"));
		try
		{
			StringEntity content = new StringEntity("Some random content");
			content.setContentType("application/json");
			try(CloseableHttpResponse response = client.execute(httpGet);)
			{
				System.out.println("response: " +response);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally {
			httpGet.releaseConnection();
		}
	}
	
	public void resetPut1() throws URISyntaxException {
		//no tag
		HttpPut httpPut = new HttpPut(new URI("http://www.example.com"));
		try
		{
			StringEntity content = new StringEntity("Some random content");
			content.setContentType("application/json");

			httpPut.setEntity(content);
			try(CloseableHttpResponse response = client.execute(httpPut);)
			{
				System.out.println("response: " +response);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally {
			httpPut.reset();
		}
	}
	
	
	
	public void resetPut2() throws URISyntaxException {
		//no tag
		HttpPut httpPut = new HttpPut(new URI("http://www.example.com"));
		try
		{
			StringEntity content = new StringEntity("Some random content");
			content.setContentType("application/json");

			httpPut.setEntity(content);
			try(CloseableHttpResponse response = client.execute(httpPut);)
			{
				System.out.println("response: " +response);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally {
			httpPut.releaseConnection();
		}
	}
}
