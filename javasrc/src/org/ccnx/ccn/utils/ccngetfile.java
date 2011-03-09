/*
 * A CCNx command line utility.
 *
 * Copyright (C) 2008-2011 Palo Alto Research Center, Inc.
 *
 * This work is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation. 
 * This work is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details. You should have received a copy of the GNU General Public
 * License along with this program; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 */

package org.ccnx.ccn.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.io.CCNFileInputStream;
import org.ccnx.ccn.io.CCNInputStream;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;

/**
 * A command-line utility for pulling files out of ccnd or a repository.
 * Note class name needs to match command name to work with ccn_run
 */
public class ccngetfile implements Usage {
	static Usage u = new ccngetfile();
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		for (int i = 0; i < args.length - 3; i++) {
			if (!CommonArguments.parseArguments(args, i, u)) {
				u.usage();
				System.exit(1);
			}
			if (CommonParameters.startArg > i)
				i = CommonParameters.startArg;
		}
		
		if (args.length < CommonParameters.startArg + 2) {
			u.usage();
			System.exit(1);
		}
		
		try {
			int readsize = 1024; // make an argument for testing...
			// If we get one file name, put as the specific name given.
			// If we get more than one, put underneath the first as parent.
			// Ideally want to use newVersion to get latest version. Start
			// with random version.
			ContentName argName = ContentName.fromURI(args[CommonParameters.startArg]);
			
			CCNHandle handle = CCNHandle.open();

			File theFile = new File(args[CommonParameters.startArg + 1]);
			if (theFile.exists()) {
				System.out.println("Overwriting file: " + args[CommonParameters.startArg + 1]);
			}
			FileOutputStream output = new FileOutputStream(theFile);
			
			long starttime = System.currentTimeMillis();
			CCNInputStream input;
			if (CommonParameters.unversioned)
				input = new CCNInputStream(argName, handle);
			else
				input = new CCNFileInputStream(argName, handle);
			if (CommonParameters.timeout != null) {
				input.setTimeout(CommonParameters.timeout); 
			}
			byte [] buffer = new byte[readsize];
			
			int readcount = 0;
			long readtotal = 0;
			//while (!input.eof()) {
			while ((readcount = input.read(buffer)) != -1){
				//readcount = input.read(buffer);
				readtotal += readcount;
				output.write(buffer, 0, readcount);
				output.flush();
			}
			if (CommonParameters.verbose)
				System.out.println("ccngetfile took: "+(System.currentTimeMillis() - starttime)+"ms");
			System.out.println("Retrieved content " + args[1] + " got " + readtotal + " bytes.");
			System.exit(0);

		} catch (ConfigurationException e) {
			System.out.println("Configuration exception in ccngetfile: " + e.getMessage());
			e.printStackTrace();
		} catch (MalformedContentNameStringException e) {
			System.out.println("Malformed name: " + args[0] + " " + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Cannot write file or read content. " + e.getMessage());
			e.printStackTrace();
		}
		System.exit(1);
	}
	
	public void usage() {
		System.out.println("usage: ccngetfile [-unversioned] [-timeout millis] [-as pathToKeystore] [-ac (access control)] <ccnname> <filename>");
	}
	
}
