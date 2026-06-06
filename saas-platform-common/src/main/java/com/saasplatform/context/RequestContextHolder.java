package com.saasplatform.context;

import java.util.Stack;

public class RequestContextHolder {
    private static final ThreadLocal<Stack<RequestContext>> CONTEXT_STACK = ThreadLocal.withInitial(Stack::new);

    public static void set(RequestContext context) {
        CONTEXT_STACK.get().push(context);
    }

    public static RequestContext get() {
        Stack<RequestContext> stack = CONTEXT_STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    public static void clear() {
        CONTEXT_STACK.get().pop();
        if (CONTEXT_STACK.get().isEmpty()) {
            CONTEXT_STACK.remove();
        }
    }

    public static void clearAll() {
        CONTEXT_STACK.remove();
    }

    public static String getCurrentTenantId() {
        RequestContext ctx = get();
        return ctx != null ? ctx.tenantId() : null;
    }

    public static String getCurrentUserId() {
        RequestContext ctx = get();
        return ctx != null ? ctx.userId() : null;
    }

    public static String getCurrentCorrelationId() {
        RequestContext ctx = get();
        return ctx != null ? ctx.correlationId() : null;
    }
}
