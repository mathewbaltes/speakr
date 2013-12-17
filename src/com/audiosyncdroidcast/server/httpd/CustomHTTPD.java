package com.audiosyncdroidcast.server.httpd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import android.util.Log;
/**
 * Our http server to send the file to clients.
 */
public class CustomHTTPD extends NanoHTTPD
{
	public static final int PORT = 59152;
	private String rootDir;
	private String file;
	
	public CustomHTTPD() throws IOException
	{
		super(PORT, null);
    }

	public Response serve(String uri, String method, Properties header, Properties parms, Properties files) 
	{
		
		File f = new File(file);
		File homeDir = null;		
		
		String fileName = f.getName();

		if (f.exists()) 
		{
			homeDir = new File(f.getParent());
		}
 
		if(homeDir != null)
		{
			Log.d("NanoHTTPD: ", "Serving file: " + f.getName());
			return super.serveFile(fileName, header, homeDir, false);
		}
		
		return null;
	}
    
    public void setFile(String file)
    {
    	this.file = file;
    }
	
}
