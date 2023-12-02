package org.example.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

public class CognitoJwtVerifier {

    private AwsCognitoRSAKeyProvider awsCognitoRSAKeyProvider = new AwsCognitoRSAKeyProvider(COGNITO_REGION, USER_POOL_ID);


    private static final String COGNITO_REGION = System.getenv("region");
    private static final String USER_POOL_ID = System.getenv("userPoolId");

    public DecodedJWT verifyToken(String token) {
        String aws_cognito_region = "us-east-1"; // Replace this with your aws cognito region
        String aws_user_pools_id = "us-east-1_7DEw1nt5r"; // Replace this with your aws user pools id
        Algorithm algorithm = Algorithm.RSA256(awsCognitoRSAKeyProvider);
        JWTVerifier jwtVerifier = JWT.require(algorithm)
                .build();
        return jwtVerifier.verify(token);
    }
}
