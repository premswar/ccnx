package test.ccn.data;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.ContentObject;
import com.parc.ccn.data.security.SignedInfo;
import com.parc.ccn.data.security.KeyLocator;
import com.parc.ccn.data.security.PublisherKeyID;
import com.parc.ccn.data.security.Signature;
import com.parc.security.crypto.certificates.BCX509CertificateGenerator;

public class ContentObjectTest {

	static final String baseName = "test";
	static final String subName2 = "smetters";
	static final String document2 = "test2.txt";	
	static public byte [] document3 = new byte[]{0x01, 0x02, 0x03, 0x04,
				0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c,
				0x0d, 0x0e, 0x0f, 0x1f, 0x1b, 0x1c, 0x1d, 0x1e,
				0x1f, 0x2e, 0x3c, 0x4a, 0x5c, 0x6d, 0x7e, 0xf};

	static ContentName name; 

	static final String rootDN = "C=US,O=Organization,OU=Organizational Unit,CN=Issuer";
	static final String endDN = "C=US,O=Final Org,L=Locality,CN=Fred Jones,E=fred@final.org";
	static final Date start = new Date(); 
	static final Date end = new Date(start.getTime() + (60*60*24*365));
	static final  String keydoc = "key";	
	static ContentName keyname;

	static KeyPair pair = null;
	static X509Certificate cert = null;
	static KeyLocator nameLoc = null;
	static public Signature signature;
	static public byte [] contenthash = new byte[32];
	static public byte [] publisherid = new byte[32];
	static PublisherKeyID pubkey = null;	
	static SignedInfo auth = null;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		try {
			name = ContentName.fromURI(new String[]{baseName, subName2, document2});
			keyname = ContentName.fromURI(new String[]{baseName, subName2, keydoc});
			
			Security.addProvider(new BouncyCastleProvider());
			
			// generate key pair
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(512); // go for fast
			pair = kpg.generateKeyPair();
			cert = 
				BCX509CertificateGenerator.GenerateX509Certificate(
					pair.getPublic(),
					rootDN,
					endDN,
					null,
					start,
					end,
					null,
					pair.getPrivate(),
					null);
			nameLoc = new KeyLocator(keyname);
			
			byte [] signaturebuf = new byte[64];
			Arrays.fill(signaturebuf, (byte)1);
			signature = new Signature(signaturebuf);
			Arrays.fill(contenthash, (byte)2);
			Arrays.fill(publisherid, (byte)3);
			
			pubkey = new PublisherKeyID(publisherid);
			
			auth = new SignedInfo(pubkey,
					new Timestamp(System.currentTimeMillis()), 
					SignedInfo.ContentType.LEAF, 
					nameLoc);
		} catch (Exception ex) {
			XMLEncodableTester.handleException(ex);
			System.out.println("Unable To Initialize Test!!!");
		}	
	}

	@Test
	public void testDecodeInputStream() {
		ContentObject co = new ContentObject(name, auth, document3, signature);
		ContentObject tdco = new ContentObject();
		ContentObject bdco = new ContentObject();
		XMLEncodableTester.encodeDecodeTest("ContentObject", co, tdco, bdco);
	}

}
