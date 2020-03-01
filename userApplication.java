/*
 * Computer Networks 1- userApplication
 * Emmanouil Michalainas AEM:9070
 * 
 * featured request codes are now invalid - session has ended
 * control the functionality of the program via main
 * (comment/uncomment) function calls
 * names of the functions are self-descriptive
 * NOTE: full execution of the program may lead to exception
 * NOTE: in case of exception, just comment the done function calls and resume execution
 */

import java.io.*;
import java.util.*;

public class userApplication
{
	private static Modem modem;
	private static final String echo="E4354\r";
	private static final String clearImage=" M4144\r";
	private static final String errorImage="G4885\r";
	private static final String track="P9250";
	private static final String ACK="Q7403\r";
	private static final String NACK="R9143\r";
	
	public static void main(String[] param) throws FileNotFoundException, IOException
	{
		modem= new Modem(80000);    
		modem.setTimeout(2000);
		modem.open("ithaki");
		System.out.print(read("\r\n\n\n"));
		
		responseTimeErrorFree();
		picture(clearImage,"clearImage.jpeg");
		picture(errorImage,"errorImage.jpeg");
		GPS();
		errors();
	}
	
	private static String read(String stop)
	{
		String ret="";
		while (!ret.endsWith(stop)) 
		{       
			int k=modem.read();         
			if (k==-1) return "Stream is not available\r";       
			ret += (char)k; 
		} 
		return ret;
		//  NOTE : Break endless loop by catching sequence "\r\n\n\n". 
		//  NOTE : Stop program execution when "NO CARRIER" is detected.
	}
	
	private static double ping(String request)
	{
		modem.write(request.getBytes());
		long t1 = System.nanoTime();
		modem.read();
		long t2 = System.nanoTime();
		return (t2-t1)/1e6;
	} 
	
	private static void responseTimeErrorFree() throws FileNotFoundException
	{
		System.out.println("------------Entering responseTimeErrorFree()------------\r");
		PrintStream fout = new PrintStream("responseTimeErrorFree.txt");
	
		long begin = System.nanoTime(); 
		while ((System.nanoTime()-begin)/1e9 < 240)
		{
			fout.println(ping(echo));
			System.out.println(read("PSTOP"));
			try {Thread.sleep(1000);}
			catch (InterruptedException e) {}
		}
	}
	
	private static void errors() throws FileNotFoundException
	{
		System.out.println("------------Entering errors()------------\r");
		PrintStream timeFout = new PrintStream("responseTimeWithErrors.txt");
		PrintStream failFout = new PrintStream("failures.txt");
		PrintStream BERFout = new PrintStream("BER.txt");
		long begin = System.nanoTime();
		long bits=0, bitErrors=0;
		
		while ((System.nanoTime()-begin)/1e9 < 240)
		{
			double time=0;
			String correct;
			ArrayList<String> fails = new ArrayList<String>();
			for (String code=ACK; ; code=NACK)
			{
				bits += 16*8;
				time += ping(code);
				correct = read("PSTOP");
				System.out.println(correct);
				
				int start=correct.indexOf('<')+1;
				int correctXor = Integer.parseInt(correct.substring(start+18,start+21));
				correct = correct.substring(start,start+16);
				
				int xor=0; 
				for (int i=0; i<16; i++) xor ^= correct.charAt(i);
				if (xor == correctXor) break;
				else fails.add(correct);
			}
			timeFout.println(time);
			failFout.println(fails.size());
			for (String wrong : fails)
				for (int i=0; i<16; i++)
					bitErrors += Integer.bitCount(wrong.charAt(i)^correct.charAt(i));
			try {Thread.sleep(800);}
			catch (InterruptedException e) {}
		}
		BERFout.println((double)bitErrors/bits);
	}
	
	private static void picture(String request, String filename) throws IOException
	{
		System.out.println("------------Entering picture()------------\r");
		FileOutputStream fout = new FileOutputStream(filename);
		
		modem.write(request.getBytes());
		String stop="";
		stop += (char)0xff;
		stop += (char)0xd9;
		String image = read(stop);
		for (int i=0; i<image.length(); i++)
			fout.write(image.charAt(i));
		read("\r\n");
	}
	
	private static void GPS() throws IOException
	{
		System.out.println("------------Entering GPS()------------\r");
		
		int deg[][] = new int[5][2];
		int min[][] = new int[5][2];
		int sec[][] = new int[5][2];
		int index=0;
		for (int i=1; index<5 && i<10000; i+=20)
		{
			modem.write(String.format("%sR=1%04d01\r",track,i).getBytes());
			System.out.print(read("\r\n"));
			String gpsTrack = read("\r\n");
			System.out.print(gpsTrack);
			for (int k=0, j=2; j<=4; j+=2, k++)
			{
				String degmin = gpsTrack.split(",")[j].split("\\.")[0];
				String secs = gpsTrack.split(",")[j].split("\\.")[1];
				
				int len = degmin.length();
				deg[index][k] = Integer.parseInt(degmin.substring(0,len-2));
				min[index][k] = Integer.parseInt(degmin.substring(len-2));
				sec[index][k] = Integer.parseInt(secs)*60/(int)Math.pow(10,secs.length());
			}
			if (index==0 || 
			(deg[index][0]-deg[index-1][0])*360 +
			(min[index][0]-min[index-1][0])*60 +
			(sec[index][0]-sec[index-1][0]) >= 4 ||
			(deg[index][1]-deg[index-1][1])*360 +
			(min[index][1]-min[index-1][1])*60 +
			(sec[index][1]-sec[index-1][1]) >= 4)
			{
				index++;
				System.out.print("index=");
				System.out.println(index);
			}
			System.out.print(read("\r\n"));
		}
		String request=track;
		for (int i=0; i<5; i++)
		{
			request+="T=";
			for (int j=1; j>=0; j--) 
				request+=String.format("%d%d%d",deg[i][j],min[i][j],sec[i][j]);
		}
		request += "\r";
		picture(request,"gpsImage.jpeg");
	}
}
