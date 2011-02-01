/*
 * A CCNx example of extending ccnb encoding/decoding.
 *
 * Copyright (C) 2008, 2009, 2010, 2011 Palo Alto Research Center, Inc.
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


/*
 * A CCNx example of extending ccnb encoding/decoding.
 *
 * Copyright (C) 2010, 2011 Palo Alto Research Center, Inc.
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


package org.ccnx.ccn.apps.examples.ccnb;

import java.util.Vector;

import org.ccnx.ccn.impl.encoding.GenericXMLEncodable;
import org.ccnx.ccn.impl.encoding.XMLDecoder;
import org.ccnx.ccn.impl.encoding.XMLEncoder;
import org.ccnx.ccn.io.content.ContentDecodingException;
import org.ccnx.ccn.io.content.ContentEncodingException;

/**
 *
 */
public class ExampleComplicated extends GenericXMLEncodable {
	
	
	/**
	 * @param string
	 * @param enumeration
	 * @param data
	 */
	public ExampleComplicated(String string, Enumeration enumeration,
			Vector<StringBinaryVector> data) {
		this.string = string;
		this.enumeration = enumeration;
		this.data = data;
	}

	/**
	 * @param string
	 * @param enumeration
	 */
	public ExampleComplicated(String string, Enumeration enumeration) {
		this.string = string;
		this.enumeration = enumeration;
		this.data = new Vector<StringBinaryVector>();
	}
	
	/**
	 * @return the string
	 */
	public String getString() {
		return string;
	}

	/**
	 * @param string the string to set
	 */
	public void setString(String string) {
		this.string = string;
	}

	/**
	 * @return the enumeration
	 */
	public Enumeration getEnumeration() {
		return enumeration;
	}

	/**
	 * @param enumeration the enumeration to set
	 */
	public void setEnumeration(Enumeration enumeration) {
		this.enumeration = enumeration;
	}

	/**
	 * @return the data
	 */
	public Vector<StringBinaryVector> getData() {
		return data;
	}

	public void addSBV(StringBinaryVector d) {
		data.add(d);
	}

	protected String string;
	protected Enumeration enumeration;
	protected Vector<StringBinaryVector> data;
	

	@Override
	public void decode(XMLDecoder decoder) throws ContentDecodingException {
		data.clear();
		decoder.readStartElement(getElementLabel());
		string = decoder.readUTF8Element(ExampleDTags.String); 

		if (decoder.peekStartElement(ExampleDTags.Enumeration)) {
			enumeration = Enumeration.getType(decoder.readBinaryElement(ExampleDTags.Enumeration)[0]); 
		} 
		while (decoder.peekStartElement(ExampleDTags.Data)) {
			StringBinaryVector d = new StringBinaryVector();
			d.decode(decoder);
			data.add(d);
		}
		decoder.readEndElement();
	}

	@Override
	public void encode(XMLEncoder encoder) throws ContentEncodingException {
		if (!validate())
			throw new ContentEncodingException("ExampleComplicated failed to validate!");
		encoder.writeStartElement(getElementLabel());
		encoder.writeElement(ExampleDTags.String, string);
		encoder.writeElement(ExampleDTags.Enumeration, new byte[]{(byte)enumeration.ordinal()});
		for (int i = 0; i < data.size(); i++) {
			StringBinaryVector sbv = data.elementAt(i);
			sbv.encode(encoder);
		}
		encoder.writeEndElement();   		
	}

	@Override
	public long getElementLabel() {
		return ExampleDTags.ExampleComplicated;
	}

	@Override
	public boolean validate() {
		if (data.size() < 1 || data.size() > 5) {
			return false;
		}
		return true;
	}

}
