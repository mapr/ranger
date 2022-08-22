/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ranger.util;

import java.util.concurrent.ThreadLocalRandom;
import java.security.SecureRandom;
import com.google.common.io.BaseEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mapr.fs.proto.Security.AuthenticationReqFull;
import com.mapr.fs.proto.Security.AuthenticationResp;
import com.mapr.fs.proto.Security.Key;
import com.mapr.fs.proto.Security.TicketAndKey;

import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import com.mapr.security.client.MapRClientSecurityException;

/**
 * The ClientSecurity class. This is the pure Java implementation of the client-side API
 * using MapR ticket authentication, used for REST API calls. The MapR challenge-response
 * handshake allows for mutual authentication of both server and client using the MapR
 * ticket-based security model.
 *
 * <p>
 * The MapR challenge-response handshake follows the RFC 7235/2616 standard. A brief description
 * of the handshake is given here. More information can be found in
 * <a href="https://tools.ietf.org/html/rfc7235#section-4.1">...</a> and RFC 2616 - Hypertext
 * Transfer Protocol. See https://tools.ietf.org/html/rfc2616#page-107.
 * <p>
 * If the server receives a request for an access-protected object, and
 * if an acceptable &quot;Authorization&quot; header has not been sent, the server
 * responds with a "401 Unauthorized" status code, and a "WWW-
 * Authenticate:" header as per the framework described in [RFC2616].
 * For MapR ticket authentication, the value of WWW-Authenticate include MAPR-Negotiate.
 * For example:
 * <pre>{@code
 * HTTP/1.1 401 Unauthorized
 * WWW-Authenticate: MAPR-Negotiate
 * }</pre>
 * If the client application receives a &quot;401 Unauthorized&quot; response for a REST API
 * request, it should parse the header to see if it contains the WWW-Authenticate property
 * with the value starting with MAPR-Negotiate, and start the negotiation process to obtain
 * an authentication token. The client should also start the negotiation process to obtain an
 * authentication token for the first time.
 * <p>
 * Assuming that the client has already done a "maprlogin password" to obtain a valid ticket
 * for the cluster, the first phase of the two-phase MapR challenge-response authentication
 * is to call the generateChallenge() method:
 * <pre>{@code
 * ClientSecurity cs = new ClientSecurity();
 * String challengeString = cs.generateChallenge ();
 * }</pre>
 * The returned challenge string and encrypted ticket is sent as an HTTP option ("OPTIONS")
 * using the "Authorization" header with the MAPR-Negotiate property to the MapR service.
 * An example of how to do this can be found in the com.mapr.security.client.examples.MapRClient
 * class, but is outside the scope of this API. An example of a Base64 encoded challenge string
 * is as follows:
 * <pre>
 * {@code
 * CihNcW/sDyVx63r25FJF0vuWNjAsdVrL1HrUivPYY8vdSelThGOPnd8zEmYCCAEVPQhUXvfjiFLP9xOIBsEGjZv4wwhZsk3ho2+lrDKFjWAHjnBqd7yQuqa+YSY7etfxkg1XK+izqqRjXkFo7zNST6swW7VmMJyo27CftvXv6wV3EFmJqcHV6ugJ3EjlHqtxlqY=
 * }</pre>
 * Using this sample challenge string, the request header looks something like this:
 * <pre>
 * {@code
 * Authorization: MAPR-Negotiate CihNcW/sDyVx63r25FJF0vuWNjAsdVrL1HrUivPYY8vdSelThGOPnd8zEmYCCAEVPQhUXvfjiFLP9xOIBsEGjZv4wwhZsk3ho2+lrDKFjWAHjnBqd7yQuqa+YSY7etfxkg1XK+izqqRjXkFo7zNST6swW7VmMJyo27CftvXv6wV3EFmJqcHV6ugJ3EjlHqtxlqY=
 * }</pre>
 * If the server successfully validates the challenge, it will return
 * a status of HTTP/1.1 200 OK, together with a response to the challenge in using the
 * "Authorization" header with the "MAPR-Negotiate" property. This would look something like
 * this:
 * <pre>
 * {@code
 * HTTP/1.1 200 OK
 * Authorization: MAPR-Negotiate WMlqqIt8Q5dcVo9wOtfVgBoSsbW8v/WQS0JWfGElUtPDb04hkQ/Zf26Fw7k=
 * }</pre>
 * Upon receiving the response from the server, the client application should verify that
 * the response is HTTP/1.1 200 OK, and that the response message contains the "Authorization"
 * header with the "MAPR-Negotiate" property. It should then extract the Base64-encoded response
 * to the challenge that is in the "MAPR-Negotiate" property from the response message. In our
 * example, this is the string
 * <pre>
 * {@code
 * WMlqqIt8Q5dcVo9wOtfVgBoSsbW8v/WQS0JWfGElUtPDb04hkQ/Zf26Fw7k=
 * }
 * </pre>
 * The client application should then pass this string into the validateServerResponseToChallenge
 * method:
 * <pre>
 * {@code
 * boolean isValidResponseToChallenge = validateServerResponseToChallenge (responseToChallenge);
 * }
 * </pre>
 * If this method returns without throwing an exception, then the server response is successfully
 * validated, and this completes the second and final phase of the challenge-response authentication.
 *
 */
public class RangerClientSecurity {
    private static final Logger LOG = LoggerFactory.getLogger(RangerClientSecurity.class);

    /**
     * The &quot;WWW-Authenticate&quot; response header. Used to determine if we are in
     * the negotiation phase.
     * <p>
     * If the server receives a request for an access-protected object, and
     * if an acceptable &quot;Authorization&quot; header has not been sent, the server
     * responds with a "401 Unauthorized" status code, and a "WWW-
     * Authenticate:" header as per the framework described in [RFC2616].
     * For MapR ticket authentication, the relevant headers would look like this:
     *
     * <pre>
     * {@code
     * HTTP/1.1 401 Unauthorized
     * WWW-Authenticate: MAPR-Negotiate
     * }
     * </pre>
     *
     * The client should parse the header to see if it contains the WWW-Authenticate
     * property with the value starting with MAPR-Negotiate, and start the negotiation
     * process.
     */
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    /**
     * The HTTP "Authorization" request header.
     * <p>
     * As per RFC 2616 (superseded by 7235), a user agent that wishes to authenticate itself with a server
     * usually, but not necessarily, after receiving a 401 response, does so by including
     * an Authorization request-header field with the request. The Authorization field
     * value consists of credentials containing the authentication information of the user
     * agent for the realm of the resource being requested. For MapR ticket authentication,
     * this is the token obtained after successfully completing the challenge-response
     * authentication.
     * <p>
     * Upon receipt of the response containing a "WWW-Authenticate" header
     * from the server, the client is expected to retry the HTTP request,
     * passing a HTTP "Authorization" header line.
     * See <a href="https://tools.ietf.org/html/rfc2616#page-107">...</a>.
     */
    public static final String AUTHORIZATION = "Authorization";

    /**
     * HTTP header used by the MAPR server end-point during an authentication sequence in case of error.
     * Example:
     * <p>
     * HTTP/1.1 403 Forbidden
     * WWW-MAPR-Err-Authenticate  Value: [Bad server key]
     */
    public static final String WWW_ERR_AUTHENTICATE = "WWW-MAPR-Err-Authenticate";

    /**
     * HTTP header prefix used by the MAPR client/server end-points during an authentication sequence.
     * See the comments for WWW_AUTHENTICATE (WWW-Authenticate).
     */
    public static final String NEGOTIATE = "MAPR-Negotiate";
    /**
     * Location of the mapr-clusters.conf file relative to the installation directory. The first line of this file is
     * used to determine the default cluster name and whether the cluster is secure. This is at
     * /conf/mapr-clusters.conf.
     */
    public static final String CLUSTER_CONFIG_LOCATION = "/conf/mapr-clusters.conf";
    /**
     * Default installation location if the MAPR_HOME environment variable is not set. This is /opt/mapr
     */
    public static final String DEFAULT_INSTALL_LOCATION = "/opt/mapr";
    /*
     * AES -256 key size is 256 bits (32 * 8)
     */
    private static final int KEY_SIZE_IN_BYTES = 32;
    /*
     * 16 byte hash to authenticate the data
     */
    private static final int TAG_SIZE_IN_BYTES = 16;
    /*
     * Number of bytes for the AES-256 GCM initialization vector (IV)
     */
    private static final int IV_SIZE_IN_BYTES = 16;
    /*
     * The current cluster name specified in the ClientSecurity constructor
     */
    private String currentClusterName;
    /*
     * The randomly generated secret. Required to verify the response
     */
    private long randomSecret;

    private Key userKey;
    private String userTicketFile;
    private boolean isClusterSecure;
    private boolean isClusterValid;
    // cldbs in the currentCluster
    private String[] cldbs;

    /**
     * Constructor. Parses the mapr-clusters.conf to determine the cluster name and whether security is enabled
     * Since no cluster is specified, the default cluster that appears in the first line of mapr-clusters.conf
     * is used
     */
    public RangerClientSecurity() {
        this(null);
    }
    /**
     * Constructor. Parses the mapr-clusters.conf to determine the cluster name and whether security is enabled.
     * Uses the cluster name passed into the parameter as the cluster for all methods in the class. You need a
     * different instance of this class to access different clusters
     *
     * @param clusterName  The cluster name
     */
    public RangerClientSecurity(String clusterName) {
        userTicketFile = null;
        isClusterSecure = false;
        randomSecret = 0;
        isClusterValid = false;
        currentClusterName = clusterName;
        parseMaprClustersConf();
    }
    /**
     * Returns the security status for the current cluster. To determine this, we look
     * line of mapr-clusters.conf with the cluster name matching the cluster name passed in
     * the constructor, and look for the secure=true flag. If set to true, then
     * security is enabled for the cluster, otherwise security is not enabled. The
     * ClientSecurity class is only applicable for secure clusters.
     *
     * @return Returns true if security is enabled for the cluster, false otherwise.
     */
    public boolean isSecurityEnabled() {
        return (isClusterSecure);
    }
    /**
     * Returns true if the cluster name is valid, false otherwise. The cluster name is valid if there is an entry
     * in mapr-cluster.conf for this cluster
     *
     * @return Returns true if the cluster name is valid, false otherwise.
     *
     */
    public boolean isClusterNameValid() {
        return (isClusterValid);
    }
    /**
     * Obtains the full path name of the file containing the user ticket. This is determined as follows:
     * <ul>
     *   <li>If the environment variable MAPR_TICKETFILE_LOCATION is set, then return the value of this
     *   environment variable.</li>
     *   <li>Otherwise, the default location of the user ticket is as follows:
     *     <ul>
     *       <li>For Windows, this is at %TEMP%\maprticket_&lt;username&gt;. Example: C:\Temp\maprticket_joe.</li>
     *       <li>For all other operating systems, this is at /tmp/maprticket_&lt;uid&gt; where &lt;uid&gt; is the effective UID of calling process.</li>
     *     </ul>
     *   </li>
     * </ul>
     * Pure Java implementation of the Security::GetUserTicketAndKeyFileLocation C API in
     * src/fs/common/credentials.h
     *
     * @return The full path name of the file containing the user ticket.
     * @throws MapRClientSecurityException Thrown when the location of the user ticket cannot be determined
     */
    public String getUserTicketAndKeyFileLocation() throws MapRClientSecurityException {
        String mapRDefaultKeyFileLocation_;
        String mapRFileNameSuffix_;
        String filePath;
        String euid_;

        filePath= System.getenv("MAPR_TICKETFILE_LOCATION");
        if (filePath != null) {
            if (!filePath.isEmpty()) {
                return filePath;
            }
        }
        String osName=System.getProperty("os.name");
        if (osName.equalsIgnoreCase("Windows")) {
            mapRDefaultKeyFileLocation_ = System.getenv("TEMP");
            mapRFileNameSuffix_ = System.getProperty("user.name");
        } else {
            mapRDefaultKeyFileLocation_ = "/tmp";
            /*
             * The default file name is /tmp/maprticket_<uid>. For example, if this ticket is for
             * user mapr and the UID of mapr is 5000, then the default file name is /tmp/maprticket_<uid>
             */
            String userName = System.getProperty ("user.name");
            ArrayList<String> command = new ArrayList<String>();
            command.add ("id");
            command.add ("-u");
            command.add (userName);
            try {
                euid_ = executeCommandAndReturnOutput(command);
            } catch (IOException e) {
                LOG.error("Unable to obtain effective UID for user " + userName + ":" + e.getMessage());
                throw new MapRClientSecurityException ("Unable to obtain effective UID for user " + userName + ":" + e.getMessage());
            } catch (InterruptedException e) {
                LOG.error("Error executing command id -u " + userName + ": " + e.getMessage());
                throw new MapRClientSecurityException ("Error execuring command id -u " + userName + ": " + e.getMessage());
            }
            mapRFileNameSuffix_ = euid_;
        }
        filePath = mapRDefaultKeyFileLocation_ + File.separator + "maprticket_" + mapRFileNameSuffix_;

        return filePath;
    }
    /**
     * Evaluates the challenge from the MapR user ticket. Call this method to return a Base64-encoded string that can be used
     * as an authentication string in a REST API call with the MAPR-Negotiate property. For example:
     *
     * <pre>
     * {@code
     * ClientSecurity cs = new ClientSecurity();
     * String authRequestBytes = cs.generateChallenge();
     * }</pre>
     *
     * @return The encrypted challenge string as a Base-64 encoded string
     * @throws MapRClientSecurityException Thrown for various errors, such as user ticket is invalid or cannot
     * be found, or the challenge cannot be derived from the user ticket
     */
    public String generateChallenge() throws MapRClientSecurityException {
        try {
            LOG.debug("Generating challenge for cluster " + currentClusterName);
            TicketAndKey ticketKey = null;
            /*
             * Obtain the user ticket, first ensuring that it has not already expired
             */
            ticketKey = authenticateIfNeeded();
            if ( ticketKey == null ) {
                LOG.error("No good client ticket found for cluster " + currentClusterName);
                throw new MapRClientSecurityException("No good client ticket found for cluster " + currentClusterName);
            }
            /*
             * Obtain the challenge. First generate a 64-bit (8-byte) random secret
             */
            userKey = ticketKey.getUserKey();
            randomSecret = generateRandomNumber();
            byte[] writeBuffer = new byte[8];
            writeBuffer[0] = (byte)(randomSecret >>> 56);
            writeBuffer[1] = (byte)(randomSecret >>> 48);
            writeBuffer[2] = (byte)(randomSecret >>> 40);
            writeBuffer[3] = (byte)(randomSecret >>> 32);
            writeBuffer[4] = (byte)(randomSecret >>> 24);
            writeBuffer[5] = (byte)(randomSecret >>> 16);
            writeBuffer[6] = (byte)(randomSecret >>>  8);
            writeBuffer[7] = (byte)(randomSecret >>>  0);
            /*
             * Build the challenge string
             */
            AuthenticationReqFull.Builder bld = AuthenticationReqFull.newBuilder();
            /*
             * Encrypt the challenge string using the user key in the ticket.
             */
            byte[] secretBytesEncrypted;
            try {
                secretBytesEncrypted = aesEncrypt(userKey.getKey().toByteArray(), writeBuffer);
            } catch (NoSuchAlgorithmException e) {
                LOG.error("AES-256 GCM not supported: " + e.getMessage());
                throw new MapRClientSecurityException ("AES-256 GCM not supported: " + e.getMessage());
            } catch (NoSuchPaddingException e) {
                LOG.error("AES-256 GCM with no padding not supported: " + e.getMessage());
                throw new MapRClientSecurityException ("AES-256 GCM with no padding not supported: " + e.getMessage());
            } catch (InvalidKeyException e) {
                LOG.error("Invalid AES-256 GCM user key: " + e.getMessage());
                throw new MapRClientSecurityException ("Invalid AES-256 GCM user key: " + e.getMessage());
            } catch (InvalidAlgorithmParameterException e) {
                LOG.error("Invalid parameters for AES-256 GCM: " + e.getMessage());
                throw new MapRClientSecurityException ("Invalid parameters for AES-256 GCM: " + e.getMessage());
            } catch (IllegalBlockSizeException e) {
                LOG.error("Illegal AES-256 GCM block size: " + e.getMessage());
                throw new MapRClientSecurityException ("Illegal AES-256 GCM block size: " + e.getMessage());
            } catch (BadPaddingException e) {
                LOG.error("Bad padding for AES-256 GCM: " + e.getMessage());
                throw new MapRClientSecurityException ("Bad padding for AES-256 GCM: " + e.getMessage());
            }
            bld.setEncryptedRandomSecret(ByteString.copyFrom(secretBytesEncrypted));
            bld.setEncryptedTicket(ticketKey.getEncryptedTicket());
            for(String cldb : cldbs)
                bld.addCldb(cldb);
            byte [] authRequestBytes = bld.build().toByteArray();
            String challengeString = null;
            try {
                /*
                 * Return the challenge as a Base64-encoded byte array
                 */
                BaseEncoding base64 = BaseEncoding.base64();
                challengeString = base64.encode(authRequestBytes);
            } catch (Exception e) {
                throw new MapRClientSecurityException("Unable to encode challenge: " + e.getMessage());
            }
            LOG.debug("Successfully obtained challenge");
            return challengeString;
        } catch (MapRClientSecurityException t) {
            LOG.error("Exception while processing ticket data: " + t.getMessage());
            throw new MapRClientSecurityException("Exception while processing ticket data",t);
        }
    }
    /**
     * Determines if the user has a valid ticket for the cluster specified in the parameter.
     *
     * @return Returns true if the user has a valid ticket for the cluster, false otherwise.
     * @throws MapRClientSecurityException Thrown for various error situations, for example, when
     *         the ticket cannot be found, or the cluster name is invalid.
     */
    public boolean hasValidTicket() throws MapRClientSecurityException {
        TicketAndKey tk = authenticateIfNeeded();
        return (tk != null);
    }
    /**
     * Returns the cluster name. This is the cluster name that is set in the constructor
     *
     * @return The cluster name as an ASCII string. This is the cluster name from the first line of mapr-clusters.conf
     */
    public String getClusterName() {
        return (currentClusterName);
    }

    /**
     * Given the response from the server to the challenge, validates the response to the challenge.
     * The server response is the Base-64 encoded string after the MAPR-Negotiate property in the
     * Authorization header. It is the responsibility of the client application to extract the
     * response to the challenge from the header and pass it in to this method as the second phase
     * of the challenge-response negotiation.
     * <p>
     * A sample header looks like this:
     * <pre>
     * {@code
     * HTTP/1.1 200 OK
     * Authorization: MAPR-Negotiate M0GtfTQNSVVzOOtTFI8cgA5MON5cb1beloL+C3k6tl9lY16VRrA6GEJCSag=
     * }
     * </pre>
     *
     * In this example, the input to this method would be the following Base64-encoded string:
     * <pre>
     * {@code
     * M0GtfTQNSVVzOOtTFI8cgA5MON5cb1beloL+C3k6tl9lY16VRrA6GEJCSag=
     * }
     * </pre>
     *
     * @param responseToChallenge  The Base64-encoded string giving the server response to the challenge
     * @return Returns true if the server response is present and valid. Otherwise, a
     *         MapRClientSecurityException is thrown with the error message.
     * @throws MapRClientSecurityException If the server response to the challenge-response negotiation
     *                                     is either not present or not valid
     */
    public boolean validateServerResponseToChallenge (String responseToChallenge) throws MapRClientSecurityException {
        byte[] base64Bytes = null;
        try {
            BaseEncoding base64 = BaseEncoding.base64();
            base64Bytes = base64.decode(responseToChallenge);
        } catch (Exception e) {
            throw new MapRClientSecurityException("Unable to decode Base64-encoded server challenge: " + e.getMessage());
        }
        /*
         * Obtain the response to the challenge
         */
        AuthenticationResp authResponse = null;
        try {
            byte[] decodedResponse = aesDecrypt(userKey.getKey().toByteArray(), base64Bytes);
            authResponse = AuthenticationResp.parseFrom(decodedResponse);
            if (authResponse == null)
                throw new MapRClientSecurityException("Response is null");
        } catch (Exception e) {
            throw new MapRClientSecurityException("Error while decrypting response " + e.getMessage());
        }

        if (authResponse.hasChallengeResponse()) {
            LOG.debug("Response to challenge found");
            long responseSecret = authResponse.getChallengeResponse();
            if (responseSecret != (randomSecret + 1))
            {
                throw new MapRClientSecurityException("Incorrect challenge response");
            }
            LOG.debug("Successfully validated server response");
        } else {
            throw new MapRClientSecurityException("No response secret");
        }
        return true;
    }
    /*
     * Encrypts the plaintext using the given key using AES-256 GCM. Used to encrypt tickets and
     * challenge string, or any other plaintext byte stream.
     *
     * Since we don't use padding, the length of the ciphertext is the same as that of the plain text.
     * The length of the returned result is always 32 byte longer than the length of the plaintext,
     * because we prefix the 16-byte IV and append the 16-byte AES-GCM tag to the ciphertext.
     *
     * @param key        The byte array containing the 32-byte key to use for the encryption.
     * @param plainText  The byte array containing the encrypted data to be decrypted
     * @return           The decrypted byte stream containing the encrypted plaintext
     * @throws NoSuchAlgorithmException  AES-GCM  is not supported
     * @throws NoSuchPaddingException    AES with no padding is not supported
     * @throws InvalidKeyException       Key is invalid
     * @throws InvalidAlgorithmParameterException AES-GCM parameters are invalid.
     * @throws IllegalBlockSizeException Block size is invalid
     * @throws BadPaddingException  Bad padding.
     *
     * @see aesDecrypt
     */
    private byte[] aesEncrypt (byte[] key, byte[] plainText)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException,IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecureRandom random = new SecureRandom();
        SecretKey secretKey = new SecretKeySpec(key, 0, key.length, "AES");
        final byte[] iv = new byte[IV_SIZE_IN_BYTES];
        random.nextBytes(iv);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE_IN_BYTES * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
        /*
         * The input plaintext:
         *
         *                                                               plainText.length
         *                  +------------------------- ... -----------------------+
         *                  |     plain text                                      |
         *                  +------------------------- ... -----------------------+
         *
         * The returned result:
         *
         *  0             15 16                                            encryptedData.length
         * +----------------+------------------------- ... -----------------------+----------------+
         * + IV             |    cipher text                                      |  tag           |
         * +----------------+------------------------- ... -----------------------+----------------+
         */
        byte[] cipherText = cipher.doFinal(plainText);
        /*
         * We need to add the 16-byte IV to the beginning of the message
         */
        byte[] cipherTextWithIV = Arrays.copyOf(iv,  iv.length + cipherText.length);
        System.arraycopy(cipherText, 0, cipherTextWithIV, iv.length, cipherText.length);

        return cipherTextWithIV;
    }
    /*
     * Decrypt the ciphertext with the given key using AES-256 GCM. Used to decrypt tickets or any
     * encrypted byte stream.
     *
     * The 16-byte IV is stored in the first 16 bytes of the encryptedData byte array, while the
     * 16-byte AES-256 GCM tag is at the end of the encrypted text. Therefore, the decrypted ticket
     * is always 32 bytes less than the length of the encrypted byte stream.
     *
     * @param key         The byte array containing the 32-byte key to use for the decryption.
     * @param cipherText  The byte array containing the encrypted data to be decrypted
     * @return            The decrypted byte stream containing the encrypted plaintext
     * @throws NoSuchAlgorithmException  AES-GCM  is not supported
     * @throws NoSuchPaddingException    AES with no padding is not supported
     * @throws InvalidKeyException       Key is invalid
     * @throws InvalidAlgorithmParameterException AES-GCM parameters are invalid.
     * @throws IllegalBlockSizeException Block size is invalid
     * @throws BadPaddingException  Bad padding.
     *
     * @see encrypt
     */
    private byte[] aesDecrypt (byte[] key, byte[] cipherText)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException,IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKey secretKey = new SecretKeySpec(key, 0, key.length, "AES");

        byte[] iv = new byte[IV_SIZE_IN_BYTES];
        for (int i=0; i<IV_SIZE_IN_BYTES; i++) {
            iv[i] = cipherText[i];
        }
        GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE_IN_BYTES * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        /*
         *  The encrypted data immediately follows the IV
         */
        byte[] cipherTextWithoutIV = Arrays.copyOfRange(cipherText, IV_SIZE_IN_BYTES, cipherText.length);
        /*
         * Sample encrypted byte stream:
         *
         *  0             15 16                                            encryptedData.length
         * +----------------+------------------------- ... -----------------------+----------------+
         * + IV             |     cipher text                                     | tag            |
         * +----------------+------------------------- ... -----------------------+----------------+
         */
        byte[] plainText = cipher.doFinal(cipherTextWithoutIV);
        return plainText;
    }
    /*
     * Executes the command given in the array "command" and returns the output as a string
     * Used mainly as an equivalent to C system calls
     */
    private String executeCommandAndReturnOutput(ArrayList<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        StringBuilder processOutput = new StringBuilder();

        try (
                BufferedReader processOutputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));)
        {
            String readLine;
            while ((readLine = processOutputReader.readLine()) != null)
            {
                processOutput.append(readLine + System.lineSeparator());
            }
            process.waitFor();
        }
        return processOutput.toString().trim();
    }
    /*
     * Determines whether the ticket is usable. We do this by ensuring that the ticket
     * has not expired
     */
    private boolean isTicketAndKeyUsable(TicketAndKey ticketAndKey) {
        long currentTimeSec = System.currentTimeMillis() / 1000;
        long expiryTimeSec = ticketAndKey.getExpiryTime();

        return currentTimeSec < expiryTimeSec;
    }
    /*
     * Given the Base-64 encoded and encrypted ticket byte stream, decode and decrypt data
     * and return the decrypted byte stream containing the ticket in Google protocol buffer format
     *
     */
    private byte[] decodeDataFromKeyFile (String encodedData) {
        byte[] encryptedData = null;
        try {
            BaseEncoding encoding = BaseEncoding.base64();
            encryptedData = encoding.decode (encodedData);
        } catch (Exception e) {
            LOG.error("Unable to decode Base-64 encoded ticket: " + e.getMessage());
            return null;
        }
        byte[] key = getKeyForKeyFile();
        byte[] decryptedData;
        try {
            decryptedData = aesDecrypt(key, encryptedData);
            return decryptedData;
        } catch (Exception e) {
            LOG.error ("Unable to decrypt data: " + e.getMessage());
            return null;
        }
    }
    /*
     * Obtain the ticket key. Use hard coded key -- all 'A's (ASCII 65) to read/write from key file
     */
    private byte[] getKeyForKeyFile() {
        byte[] keybuf = new byte[KEY_SIZE_IN_BYTES];
        for (int i=0; i<KEY_SIZE_IN_BYTES; i++)
            keybuf[i] = 65;
        return keybuf;
    }
    /*
     * Parses the mapr-clusters.conf file to obtain the current cluster name and the
     * cluster security status. Invoked by the constructor
     */
    private void parseMaprClustersConf() {
        String clusterConfig;
        String installDir;
        String maprHomeDir= System.getenv("MAPR_HOME");
        if (maprHomeDir != null) {
            if (!maprHomeDir.isEmpty()) {
                installDir = maprHomeDir;
            } else {
                installDir = DEFAULT_INSTALL_LOCATION;
            }
        } else
            installDir = DEFAULT_INSTALL_LOCATION;

        clusterConfig = installDir + CLUSTER_CONFIG_LOCATION;

        try {
            File file = new File(clusterConfig);
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            /*
             * Each line of mapr-cluster.conf has a format like this:
             * chyelin61.cluster.com secure=true node-61.lab:7222
             */
            boolean firstLine = true;
            String thisCluster;
            while ((line = bufferedReader.readLine()) != null) {
                /*
                 * At this point, we have a line containing the cluster name and its
                 * security status. Split this into space-delimited tokens and look
                 * for the one with the secure=flag
                 */
                String[] elements = line.split(" ");
                /*
                 * This is the cluster name for this entry
                 */
                thisCluster = elements[0];
                if (firstLine) {
                    if (currentClusterName == null) {
                        currentClusterName = thisCluster;
                    }
                    firstLine = false;
                }
                /*
                 * We need to find an entry with a matching cluster name
                 */
                if (!currentClusterName.equals(thisCluster))
                    continue;
                /*
                 * If we get here, we have a matching cluster entry
                 */
                isClusterValid = true;
                int hostIdx = 1;
                /*
                 * See if the cluster is secure
                 */
                for (int i = 1; i<elements.length; i++) {
                    if (elements[i].startsWith("secure=")) {
                        String[] secureSetting = elements[i].split ("=");
                        isClusterSecure=false;
                        if (secureSetting[1].equalsIgnoreCase("true")) {
                            isClusterSecure=true;
                        }
                        hostIdx = i + 1;
                        break;
                    }
                }
                // retrieve all cldbs for this cluster
                cldbs = new String[elements.length - hostIdx];
                for (int i = hostIdx; i < elements.length; i++) {
                    String[] nics = elements[i].split(";");
                    // only care about the first nic
                    cldbs[i - hostIdx] = nics[0];
                    i++;
                }
            }
            bufferedReader.close();
        } catch (IOException e) {
            LOG.error ("Failed to parse mapr-clusters.conf: " + e.getMessage());
        }
    }

    /*
     * Returns an 8 byte (long) random number
     *
     * @return Returns a 64-bit (8-byte) random number
     */
    private long generateRandomNumber () {
        return (ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    }

    /*
     * Ensures that we have a ticket to the specified cluster that has not yet expired.
     * If so, return the ticket.
     * @throws MapRClientException if the {@code cluster} is null
     */
    private TicketAndKey authenticateIfNeeded() throws MapRClientSecurityException {
        /*
         * bail if security not enabled
         */
        if (!isSecurityEnabled()) {
            LOG.debug("security appears to be off");
            return null;
        }
        TicketAndKey tk = null;

        try {
            tk = getTicketAndKeyForCluster();
        } catch (MapRClientSecurityException e) {
            LOG.info("Unable to obtain user ticket for cluster " + currentClusterName);
            throw new MapRClientSecurityException (e);
        }
        /*
         * have we already loaded a ticket for the cluster??
         */
        if (isTicketAndKeyUsable(tk)) {
            LOG.debug("Already have good ticket, done");
            return tk;
        }
        return null;
    }
    /*
     * Returns the ticket and user key by obtaining the entry from the client ticket file and decoding/
     * decrypting it. Returns the ticket including the user key in Google protocol buffer format
     */
    private TicketAndKey getTicketAndKeyForCluster() throws MapRClientSecurityException {
        TicketAndKey clientTicketAndKey = null;
        byte[] decryptedTicketAndKeyStream = null;
        String encryptedClientTicketAndKey = null;
        /*
         * Find the ticket in the user ticket file. This will contain one or more lines in the format
         * <cluster-name, encoded-ticket>. For example:
         *
         * chyelin61.cluster.com KNsOu2IGqqCynmgjgNdvplTKHOrewrA14MIaNIpPZTYHeaiRWQcUZ6NDROyavg1z18cwFWCPA4TXBz7vol2XsfuYS
         * YGKqfEBIvNlyTqdjes9fIyr5asNtGoCsK85ySn9FJMP3QThviO4gSZ3jDUtH0yajvJGIGgLj49iC7SoAUs9sO1jdcIRCUAxetc9ay6unx9l4Ypa
         * qJ8O5jyX+l1RtglXqPfR3zb9Ryu950BBmUa0c+hEuMt1cRuODmoZSxFPk8W5NLhMX35z
         *
         * We need to find the ticket that matches the default cluster name found in mapr-clusters.conf
         */
        try {
            /*
             * Get the location of the user ticket. This will be something like /tmp/maprticket_0
             */
            userTicketFile = getUserTicketAndKeyFileLocation();
            File ticketFile = new File(userTicketFile);
            boolean exists = ticketFile.exists();
            if (!exists) {
                throw new MapRClientSecurityException("Ticket file " + userTicketFile + " does not exist");
            }
            FileReader fileReader = new FileReader(ticketFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            /*
             * We want to retrieve the line in the MapR ticket with the cluster name matching the cluster name passed
             * in our constructor
             */
            boolean found = false;
            while ((line = bufferedReader.readLine()) != null) {
                String[] elements = line.split (" ");
                String thisClusterName = elements[0];
                if (thisClusterName.equals(currentClusterName)) {
                    /*
                     * We found our ticket
                     */
                    encryptedClientTicketAndKey = elements[1];
                    found = true;
                    break;
                }
            }
            bufferedReader.close();
            if (!found) {
                throw new MapRClientSecurityException ("No user ticket found for cluster " + currentClusterName + " in " + userTicketFile);
            }
            if (encryptedClientTicketAndKey != null) {
                decryptedTicketAndKeyStream = decodeDataFromKeyFile (encryptedClientTicketAndKey);
            } else {
                throw new MapRClientSecurityException ("Unable to obtain encrypted user ticket");
            }
        } catch (IOException e) {
            LOG.error("IO Exception: " + e.getMessage());
            throw new MapRClientSecurityException ("I/O Exception: " + e.getMessage());
        }
        /*
         * Parse this into the Google protocol buffer structure
         */
        try {
            clientTicketAndKey = TicketAndKey.parseFrom(decryptedTicketAndKeyStream);
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse decrypted user ticket byte stream");
            e.printStackTrace();
        }
        return (clientTicketAndKey);
    }
}
