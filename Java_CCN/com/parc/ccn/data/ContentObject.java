package com.parc.ccn.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;
import java.util.logging.Level;

import javax.xml.stream.XMLStreamException;

import com.parc.ccn.Library;
import com.parc.ccn.config.SystemConfiguration;
import com.parc.ccn.config.SystemConfiguration.DEBUGGING_FLAGS;
import com.parc.ccn.data.security.KeyLocator;
import com.parc.ccn.data.security.PublisherKeyID;
import com.parc.ccn.data.security.Signature;
import com.parc.ccn.data.security.SignedInfo;
import com.parc.ccn.data.security.SignedInfo.ContentType;
import com.parc.ccn.data.util.BinaryXMLCodec;
import com.parc.ccn.data.util.GenericXMLEncodable;
import com.parc.ccn.data.util.XMLCodecFactory;
import com.parc.ccn.data.util.XMLDecoder;
import com.parc.ccn.data.util.XMLEncodable;
import com.parc.ccn.data.util.XMLEncoder;
import com.parc.ccn.security.crypto.CCNDigestHelper;
import com.parc.ccn.security.crypto.CCNSignatureHelper;
import com.parc.ccn.security.keys.KeyManager;

/**
 * Utility class for grouping all of the bits associated
 * with a piece of content.
 * @author smetters
 *
 */
public class ContentObject extends GenericXMLEncodable implements XMLEncodable, Comparable<ContentObject> {
	
	public static boolean DEBUG_SIGNING = false;
	
	protected static final String CONTENT_OBJECT_ELEMENT = "ContentObject";
	protected static final String CONTENT_ELEMENT = "Content";
	
	protected ContentName _name;
	protected SignedInfo _signedInfo;
    protected byte [] _content;
	protected Signature _signature; 
     
    /**
     * We copy the content when we get it. The intent is for this object to
     * be immutable. Rules for constructor immutability are explained well
     * here: 
     * @param digestAlgorithm
     * @param name
     * @param signedInfo
     * @param content
     * @param signature already immutable
     */
    public ContentObject(String digestAlgorithm, // prefer OID
    					 ContentName name,
    					 SignedInfo signedInfo,
    					 byte [] content,
    					 Signature signature
    					 ) {
    	_name = name;
    	_signedInfo = signedInfo;
    	_content = content.clone();
    	_signature = signature;
    }
    
    public ContentObject(ContentName name, SignedInfo signedInfo, byte [] content,
    					 Signature signature) {
    	this(CCNDigestHelper.DEFAULT_DIGEST_ALGORITHM, name, signedInfo, content, signature);
    }
    
    /**
     * Generate a signedInfo and a signature.
     * @throws SignatureException 
     * @throws InvalidKeyException 
     */
    public ContentObject(ContentName name, 
    					 SignedInfo signedInfo,
    					 byte [] content, PrivateKey signingKey) throws InvalidKeyException, SignatureException {
    	this(name, signedInfo, content, (Signature)null);
    	_signature = sign(name, signedInfo, content, signingKey);
    }
    
    /**
     * Used for testing.
     */
	public static ContentObject buildContentObject(ContentName name, byte[] contents, 
						  						   PublisherKeyID publisher,
						  						   KeyManager keyManager) {
		try {
			if (null == keyManager) {
				keyManager = KeyManager.getDefaultKeyManager();
			}
			PrivateKey signingKey = keyManager.getSigningKey(publisher);
			if ((null == publisher) || (null == signingKey)) {
				signingKey = keyManager.getDefaultSigningKey();
				publisher = keyManager.getPublisherKeyID(signingKey);
			}
			KeyLocator locator = keyManager.getKeyLocator(signingKey);
			return new ContentObject(name, new SignedInfo(publisher, SignedInfo.ContentType.LEAF, locator), contents, signingKey);
		} catch (Exception e) {
			Library.logger().warning("Cannot build content object for publisher: " + publisher);
			Library.infoStackTrace(e);
		}
		return null;
	}
	
	public static ContentObject buildContentObject(ContentName name, byte [] contents) {
		return buildContentObject(name, contents, null, null);
	}
    			   
	public static ContentObject buildContentObject(ContentName name, byte [] contents, PublisherKeyID publisher) {
		return buildContentObject(name, contents, publisher, null);
	}

	public ContentObject() {} // for use by decoders
    
    public ContentObject clone() {
    	// Constructor will clone the _content, signedInfo and signature are immutable types.
    	return new ContentObject(_name.clone(), _signedInfo, _content, _signature);
    }
    
    /**
     * DKS -- return these as final for now; stopgap till refactor that makes
     * internal version final.
     * @return
     */
    public final ContentName name() { return _name; }
    
    public final SignedInfo signedInfo() { return _signedInfo;}
    
    public final byte [] content() { return _content; }
    
    public final Signature signature() { return _signature; }
    
	public void decode(XMLDecoder decoder) throws XMLStreamException {
		decoder.readStartElement(CONTENT_OBJECT_ELEMENT);

		_signature = new Signature();
		_signature.decode(decoder);
		
		_name = new ContentName();
		_name.decode(decoder);
		
		_signedInfo = new SignedInfo();
		_signedInfo.decode(decoder);
		
		_content = decoder.readBinaryElement(CONTENT_ELEMENT);

		decoder.readEndElement();
	}

	public void encode(XMLEncoder encoder) throws XMLStreamException {
		if (!validate()) {
			throw new XMLStreamException("Cannot encode " + this.getClass().getName() + ": field values missing.");
		}
		encoder.writeStartElement(CONTENT_OBJECT_ELEMENT);

		signature().encode(encoder);
		name().encode(encoder);
		signedInfo().encode(encoder);

		// needs to handle null content
		encoder.writeElement(CONTENT_ELEMENT, _content);

		encoder.writeEndElement();   		
	}
	
	public boolean validate() { 
		// recursive?
		// null content ok
		return ((null != name()) && (null != signedInfo()) && (null != signature()));
	}

	@Override
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = PRIME * result + ((_name == null) ? 0 : _name.hashCode());
		result = PRIME * result + ((_signedInfo == null) ? 0 : _signedInfo.hashCode());
		result = PRIME * result + ((_signature == null) ? 0 : _signature.hashCode());
		result = PRIME * result + Arrays.hashCode(_content);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final ContentObject other = (ContentObject) obj;
		if (_name == null) {
			if (other.name() != null)
				return false;
		} else if (!_name.equals(other.name()))
			return false;
		if (_signedInfo == null) {
			if (other.signedInfo() != null)
				return false;
		} else if (!_signedInfo.equals(other.signedInfo()))
			return false;
		if (_signature == null) {
			if (other.signature() != null)
				return false;
		} else if (!_signature.equals(other.signature()))
			return false;
		if (!Arrays.equals(_content, other._content))
			return false;
		return true;
	}
	
	/**
	 * Generate a signature on a name-content mapping. This
	 * signature is specific to both this content signedInfo
	 * and this name. The SignedInfo no longer contains
	 * a proxy for the content, so we sign the content itself
	 * directly.  This is used with simple algorithms that don't
	 * generate a witness.
	 * DKS -- TODO - do we sign the content or a hash of the content?
	 * @throws SignatureException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 */
	public static Signature sign(ContentName name, 
					 		   SignedInfo signedInfo,
					 		   byte [] content,
					 		   String digestAlgorithm, 
					 		   PrivateKey signingKey) 
		throws SignatureException, InvalidKeyException, NoSuchAlgorithmException {
		
		// Build XML document
		byte [] signature = null;
		
		try {
			byte [] toBeSigned = prepareContent(name, signedInfo,content);
			signature = 
				CCNSignatureHelper.sign(digestAlgorithm, 
									 toBeSigned,
									 signingKey);
			if (SystemConfiguration.checkDebugFlag(DEBUGGING_FLAGS.DEBUG_SIGNATURES)) {
				SystemConfiguration.outputDebugData(name, toBeSigned);
			}
			
		} catch (XMLStreamException e) {
			Library.handleException("Exception encoding internally-generated XML name!", e);
			throw new SignatureException(e);
		}
		return new Signature(digestAlgorithm, null, signature);
	}
	
	public static Signature sign(ContentName name, 
					 		   SignedInfo signedInfo,
					 		   byte [] content,
					 		   PrivateKey signingKey) 
			throws SignatureException, InvalidKeyException {
		try {
			return sign(name, signedInfo, content, CCNDigestHelper.DEFAULT_DIGEST_ALGORITHM, signingKey);
		} catch (NoSuchAlgorithmException e) {
			Library.logger().warning("Cannot find default digest algorithm: " + CCNDigestHelper.DEFAULT_DIGEST_ALGORITHM);
			Library.warningStackTrace(e);
			throw new SignatureException(e);
		}
	}
	
	public boolean verify(PublicKey publicKey) 
					throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, 
								XMLStreamException, InterruptedException {
		return verify(this, publicKey);
	}
	
	/**
	 * Want to verify a content object. First compute the 
	 * witness result (e.g. Merkle path root, or possibly content
	 * proxy), and make it available to the caller if caller just
	 * needs to check whether it matches a previous round. Then
	 * verify the actual signature.
	 * 
	 * @param verifySignature If we have a collection of blocks
	 * 	 all authenticated by the public key signature, we may
	 * 	 only need to verify that signature once. If verifySignature
	 *   is true, we do that work. If it is false, we simply verify
	 *   that this piece of content matches that signature; assuming 
	 *   that the caller has already verified that signature. If you're
	 *   not sure what all this means, you shouldn't be calling this
	 *   one; use the simple verify above.
	 * @param publicKey If the caller already knows a public key
	 *   that should be used to verify the signature, they can
	 *   pass it in. Otherwise, the key locator in the object
	 *   will be used to find the key.
	 * @throws SignatureException 
	 * @throws XMLStreamException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 * @throws InterruptedException 
	 */
	public static boolean verify(ContentObject object,
								 PublicKey publicKey) throws SignatureException, InvalidKeyException, NoSuchAlgorithmException, XMLStreamException, InterruptedException {
		
		// Start with the cheap part. Derive the content proxy that was signed. This is
		// either the root of the MerkleHash tree, the content itself, or the digest of
		// the content. 
		byte [] contentProxy = null;
		try {
			// Callers that think they don't need to recompute the signature can just compute
			// the proxy and check.
			// The proxy may be dependent on the whole object. If there is a proxy, signature
			// is over that. Otherwise, signature is over hash of the content and name and signedInfo.
			contentProxy = object.computeProxy();
		} catch (CertificateEncodingException e) {
			Library.logger().info("Encoding exception attempting to verify content digest for object: " + object.name() + ". Signature verification fails.");
			return false;
		}
		
		if (null != contentProxy) {
			return CCNSignatureHelper.verify(contentProxy, object.signature().signature(), object.signature().digestAlgorithm(), publicKey);
		}
	
		return verify(object.name(), object.signedInfo(), object.content(), object.signature(), publicKey);
	}

	/**
	 * Verify the public key signature on a content object.
	 * Does not verify that the content matches the signature,
	 * merely that the signature over the name and content
	 * signedInfo is correct and was performed with the
	 * indicated public key.
	 * @param contentProxy the proxy for the content that was signed. This could
	 * 	be the content itself, a digest of the content, or the root of a Merkle hash tree.
	 * @return
	 * @throws SignatureException 
	 * @throws XMLStreamException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 * @throws InterruptedException 
	 */
	public static boolean verify(
			ContentName name,
			SignedInfo signedInfo,
			byte [] content,
			Signature signature,
			PublicKey publicKey) throws SignatureException, InvalidKeyException, NoSuchAlgorithmException, XMLStreamException, InterruptedException {
	
		if (null == publicKey) {
			// Get a copy of the public key.
			// Simple routers will install key manager that
			// will just pull from CCN.
			try {
				publicKey = 
					KeyManager.getKeyManager().getPublicKey(
						signedInfo.publisherKeyID(),
						signedInfo.keyLocator());

				if (null == publicKey) {
					throw new SignatureException("Cannot obtain public key to verify object: " + name + ". Key locator: " + 
						signedInfo.keyLocator());
				}
			} catch (IOException e) {
				throw new SignatureException("Cannot obtain public key to verify object: " + name + ". Key locator: " + 
						signedInfo.keyLocator() + " exception: " + e.getMessage(), e);				
			}
		}
	
		byte [] preparedContent = prepareContent(name, signedInfo, content); 
		// Now, check the signature.
		boolean result = 
			CCNSignatureHelper.verify(preparedContent,
					signature.signature(),
					(signature.digestAlgorithm() == null) ? CCNDigestHelper.DEFAULT_DIGEST_ALGORITHM : signature.digestAlgorithm(),
							publicKey);
		if (!result) {
			Library.logger().warning("Verification failure: " + name + " timestamp: " + signedInfo.timestamp() + " signed content: " + 
										CCNDigestHelper.printBytes(CCNDigestHelper.digest(preparedContent),SystemConfiguration.DEBUG_RADIX));
			SystemConfiguration.logObject(Level.FINEST, "Verification failure:", new ContentObject(name, signedInfo, content, signature));
			if (SystemConfiguration.checkDebugFlag(DEBUGGING_FLAGS.DEBUG_SIGNATURES)) {
				SystemConfiguration.outputDebugData(name, new ContentObject(name, signedInfo, content, signature));
			}
		} else {
			Library.logger().finer("Verification success: " + name + " timestamp: " + signedInfo.timestamp() + " signed content: " + new BigInteger(1, CCNDigestHelper.digest(preparedContent)).toString(SystemConfiguration.DEBUG_RADIX));
		}
		return result;
		
	}

	public static boolean verify(byte[] proxy, byte [] signature, SignedInfo signedInfo,
			String digestAlgorithm, PublicKey publicKey) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, InterruptedException {
		if (null == publicKey) {
			// Get a copy of the public key.
			// Simple routers will install key manager that
			// will just pull from CCN.
			try {
				publicKey = 
					KeyManager.getKeyManager().getPublicKey(
						signedInfo.publisherKeyID(),
						signedInfo.keyLocator());

				if (null == publicKey) {
					throw new SignatureException("Cannot obtain public key to verify object. Key locator: " + 
						signedInfo.keyLocator());
				}
			} catch (IOException e) {
				throw new SignatureException("Cannot obtain public key to verify object. Key locator: " + 
						signedInfo.keyLocator() + " exception: " + e.getMessage(), e);				
			}
		}
	
		// Now, check the signature.
		boolean result = 
			CCNSignatureHelper.verify(proxy,
					signature,
					(digestAlgorithm == null) ? CCNDigestHelper.DEFAULT_DIGEST_ALGORITHM : digestAlgorithm,
							publicKey);
		return result;
	}

	public byte [] computeProxy() throws CertificateEncodingException, XMLStreamException {
		// Given a witness and an object, compute the proxy.
		if (null == content())
			return null;
		if ((null == signature()) || (null == signature().witness())) {
			return null;
		}
		// Have to eventually handle various forms of witnesses...
		byte [] blockDigest =
			CCNDigestHelper.digest(
				CCNDigestHelper.DEFAULT_DIGEST_ALGORITHM, 
				prepareContent(name(), signedInfo(),content()));
		return signature().computeProxy(blockDigest, true);
	}
	
	/**
	 * Prepare digest for signature.
	 * @return
	 */
	public static byte [] prepareContent(ContentName name, 
										SignedInfo signedInfo, byte [] content) throws XMLStreamException {
		if ((null == name) || (null == signedInfo) || (null == content)) {
			Library.logger().info("Name, signedInfo and content must not be null.");
			throw new XMLStreamException("prepareContent: name, signedInfo and content must not be null.");
		}
		
		// Do setup. Binary codec doesn't write a preamble or anything.
		// If allow to pick, text encoder would sometimes write random stuff...
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		XMLEncoder encoder = XMLCodecFactory.getEncoder(BinaryXMLCodec.CODEC_NAME);
		encoder.beginEncoding(baos);
		
		// We include the tags in what we verify, to allow routers to merely
		// take a chunk of data from the packet and sign/verify it en masse
		name.encode(encoder);
		signedInfo.encode(encoder);
		// We treat content as a blob according to the binary codec. Want to always
		// sign the same thing, plus it's really hard to do the automated codec
		// stuff without doing a whole document, unless we do some serious
		// rearranging.
		encoder.writeElement(CONTENT_ELEMENT, content);

		encoder.endEncoding();	

		return baos.toByteArray();
	}
	
	public byte [] contentDigest() {
		 return CCNDigestHelper.digest(CCNDigestHelper.DEFAULT_DIGEST_ALGORITHM, content());
	}
	
	public static byte [] contentDigest(String content) {
		 return CCNDigestHelper.digest(CCNDigestHelper.DEFAULT_DIGEST_ALGORITHM, content.getBytes());
	}
	
	public static byte [] contentDigest(byte [] content) {
		 return CCNDigestHelper.digest(CCNDigestHelper.DEFAULT_DIGEST_ALGORITHM, content);
	}
	
	public int compareTo(ContentObject o) {
		return name().compareTo(o.name());
	}
	
	/**
	 * Type-checkers for built-in types.
	 */
	public boolean isType(ContentType type) {
		return signedInfo().type().equals(type);
	}

	public boolean isFragment() {
		return isType(ContentType.FRAGMENT);
	}
	
	public boolean isLink() {
		return isType(ContentType.LINK);
	}
	
	public boolean isLeaf() {
		return isType(ContentType.LEAF);
	}
	
	public boolean isCollection() {
		return isType(ContentType.COLLECTION);
	}
	
	public boolean isSession() {
		return isType(ContentType.SESSION);
	}
	
	public boolean isKey() {
		return isType(ContentType.KEY);
	}
}
