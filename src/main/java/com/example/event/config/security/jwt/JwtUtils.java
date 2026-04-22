package com.example.event.config.security.jwt;

import com.example.event.constant.ErrorCode;
import com.example.event.entity.User;
import com.example.event.exception.JwtAuthenticationException;
import com.example.event.repository.UserRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtUtils {
    @Value("${jwt.secret}")
    private String jwtSecretKey;
    @Value("${jwt.access-expiration}")
    private int accessExpiration;
    @Value("${jwt.refresh-expiration}")
    private int refreshExpiration;
    @Value("${jwt.verify-expiration}")
    private int verifyExpiration;
    private final UserRepository userRepository;

    public String generateToken(String email, String sid, String type) {
        Date now = new Date();
        Date expirationDate = null;
        User user = Optional.ofNullable(userRepository.findUserByEmail(email)).orElseThrow(
                () -> new UsernameNotFoundException("Không tìm thấy user " + email)
        );
        JwtBuilder builder = Jwts.builder()
                .setSubject(email)
                .claim("id", user.getId())
                .claim("type", type)
                .setIssuedAt(now);
        switch (type.toLowerCase()) {
            case "access":
                expirationDate = new Date(now.getTime() + accessExpiration);
                builder.setExpiration(expirationDate)
                        .claim("sid", sid);
                break;
            case "refresh":
                expirationDate = new Date(now.getTime() + refreshExpiration);
                builder.setExpiration(expirationDate);
                break;
            case "verify":
                expirationDate = new Date(now.getTime() + verifyExpiration);
                builder.setExpiration(expirationDate);
                break;
            default:
                break;
        }
        String jwt = builder
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
        return jwt;
    }

    private Key getKey() {
        byte[] keyBytes = jwtSecretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String getUserNameFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (ExpiredJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_EXPIRED);
        } catch (SignatureException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_SIGNATURE_INVALID);
        } catch (MalformedJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_MALFORMED);
        } catch (UnsupportedJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_UNSUPPORTED);
        } catch (IllegalArgumentException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_ILLEGAL_ARGUMENT);
        }
    }

    public String getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.get("id").toString();
        } catch (ExpiredJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_EXPIRED);
        } catch (SignatureException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_SIGNATURE_INVALID);
        } catch (MalformedJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_MALFORMED);
        } catch (UnsupportedJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_UNSUPPORTED);
        } catch (IllegalArgumentException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_ILLEGAL_ARGUMENT);
        }
    }

    public Date getExpiryDate(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getExpiration();
        } catch (ExpiredJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_EXPIRED);
        } catch (SignatureException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_SIGNATURE_INVALID);
        } catch (MalformedJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_MALFORMED);
        } catch (UnsupportedJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_UNSUPPORTED);
        } catch (IllegalArgumentException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_ILLEGAL_ARGUMENT);
        }
    }

    public boolean isTokenExpried(String token) {
        try {
            Date expriationDate = Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody().getExpiration();
            return expriationDate.before(new Date());
        } catch (ExpiredJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_EXPIRED);
        } catch (SignatureException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_SIGNATURE_INVALID);
        } catch (MalformedJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_MALFORMED);
        } catch (UnsupportedJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_UNSUPPORTED);
        } catch (IllegalArgumentException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_ILLEGAL_ARGUMENT);
        }
    }

    public boolean validateToken(String token) {
        try {
//            log.debug(String.valueOf(blackListTokenRedisService.isBlackListToken(token)));
            return !isTokenExpried(token);
        } catch (JwtAuthenticationException e) {
            throw e;
        }
    }

    public Claims extractAllClaims(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims;
        } catch (ExpiredJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_EXPIRED);
        } catch (SignatureException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_SIGNATURE_INVALID);
        } catch (MalformedJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_MALFORMED);
        } catch (UnsupportedJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_UNSUPPORTED);
        } catch (IllegalArgumentException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_ILLEGAL_ARGUMENT);
        }
    }

}
