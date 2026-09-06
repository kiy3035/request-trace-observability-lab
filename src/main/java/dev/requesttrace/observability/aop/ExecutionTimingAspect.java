package dev.requesttrace.observability.aop;

import dev.requesttrace.observability.config.SlowThresholdProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ExecutionTimingAspect {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTimingAspect.class);

    private final SlowThresholdProperties thresholds;

    public ExecutionTimingAspect(SlowThresholdProperties thresholds) {
        this.thresholds = thresholds;
    }

    @Pointcut("execution(public * dev.requesttrace.observability.order.*Controller.*(..))"
            + " || execution(public * dev.requesttrace.observability.demo.*Controller.*(..))")
    void controllerMethods() {
    }

    @Pointcut("execution(public * dev.requesttrace.observability.order.*Service.*(..))"
            + " || execution(public * dev.requesttrace.observability.demo.*Service.*(..))")
    void serviceMethods() {
    }

    @Around("controllerMethods()")
    public Object measureController(ProceedingJoinPoint joinPoint) throws Throwable {
        return measure(joinPoint, "CONTROLLER", "CONTROLLER_END", Long.MAX_VALUE);
    }

    @Around("serviceMethods()")
    public Object measureService(ProceedingJoinPoint joinPoint) throws Throwable {
        return measure(joinPoint, "SERVICE", "SERVICE_END", thresholds.serviceMs());
    }

    private Object measure(
            ProceedingJoinPoint joinPoint,
            String layer,
            String event,
            long slowThresholdMs
    ) throws Throwable {
        long startedAt = System.nanoTime();
        boolean success = false;
        Throwable failure = null;
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            boolean slow = elapsedMs >= slowThresholdMs;
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            log.atInfo()
                    .addKeyValue("layer", layer)
                    .addKeyValue("event", event)
                    .addKeyValue("class", signature.getDeclaringTypeName())
                    .addKeyValue("method", signature.getName())
                    .addKeyValue("elapsedMs", elapsedMs)
                    .addKeyValue("slow", slow)
                    .addKeyValue("success", success)
                    .addKeyValue("exception", failure == null ? null : failure.getClass().getSimpleName())
                    .log("{} execution completed", layer);
            if ("SERVICE".equals(layer) && slow) {
                log.atWarn()
                        .addKeyValue("layer", layer)
                        .addKeyValue("event", "SLOW_SERVICE")
                        .addKeyValue("class", signature.getDeclaringTypeName())
                        .addKeyValue("method", signature.getName())
                        .addKeyValue("elapsedMs", elapsedMs)
                        .addKeyValue("slow", true)
                        .log("Slow service detected");
            }
        }
    }
}

