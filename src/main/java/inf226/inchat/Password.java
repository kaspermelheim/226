package inf226.inchat;

import com.lambdaworks.crypto.SCrypt;
import inf226.util.Maybe;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Class for creating valid passwords.
 */
public final class Password {
	public final byte[] password;
	public byte[] salt = new byte[16];

	/**
	 * Standard constructor that only takes one parameter
	 * @param pass - Password as string.
	 * @throws IllegalArgumentException - thrown if string password does not meet NIST requirements.
	 */
	public Password(String pass) throws Exception {
		if(isAllowedPass(pass)) {
			SecureRandom sr = new SecureRandom();
			sr.nextBytes(salt);
			this.password = SCrypt.scrypt(pass.getBytes(), salt, 65536, 16, 1, 32);
		}else {
			throw new Exception("Password not strong enough.");
		}
	}

	/**
	 * Constructor that takes two parameters, pass and salt as byte.
	 * @param pass
	 * @param salt
	 */
	public Password(byte[] pass, byte[] salt) {
		this.password = pass;
		this.salt = salt;
	}

	/**
	 * Method for creating password when trying to login.
	 * @param pass
	 * @param salt
	 * @return
	 */
	public static Maybe<Password> createPass(byte[] pass, byte[] salt) {
		try {
			return Maybe.just(new Password(pass, salt));
		}
		catch (Exception e){
			e.printStackTrace();
			return Maybe.nothing();
		}
	}

	/**
	 * Method for creating password when trying to register.
	 * @param pass
	 * @return
	 */
	public static Maybe<Password> createPassReg(String pass) {
		try {
			return Maybe.just(new Password(pass));
		}
		catch (Exception e) {
			e.printStackTrace();
			return Maybe.nothing();
		}
	}

	@Override
	public String toString() {
		return Arrays.toString(this.password);
	}

	/**
	 * Method for converting string to byte.
	 * @param salt - string input
	 * @return byte
	 */
	public static byte[] toByte(String salt) {
		String str = salt.replace("[", "").replace("]", "").replace(" ", "");
		String[] arr = str.split(",");
		byte[] byt = new byte[arr.length];
		for (int j = 0; j < arr.length; j++) {
			byt[j] = Byte.parseByte(arr[j]);
		}
		return byt;
	}

	/**
	 * Check if password meets NIST requirements.
	 * @param pass
	 * @return
	 */
	private boolean isAllowedPass(String pass) {
		if(pass != null && pass.length() >= 8 && pass.length() <= 64){
			return true;
		}
		return false;
	}
}

