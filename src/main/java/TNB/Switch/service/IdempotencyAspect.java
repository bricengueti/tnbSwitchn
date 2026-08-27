package TNB.Switch.service;
import TNB.Switch.annotation.IdempotencyKey;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;

@Aspect
@Component
public class IdempotencyAspect {

    private final IdempotencyService idempotencyService;

    public IdempotencyAspect(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Around("@annotation(TNB.Switch.service.Idempotent)")
    public Object aroundIdempotent(ProceedingJoinPoint joinPoint) throws Throwable {
        String key = extractIdempotencyKey(joinPoint);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Clé d'idempotence manquante sur un appel @Idempotent"
            );
        }
        return idempotencyService.executeOnce(key, joinPoint::proceed);
    }

    private String extractIdempotencyKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(IdempotencyKey.class)) {
                return (String) args[i];
            }
        }
        return null;
    }
}