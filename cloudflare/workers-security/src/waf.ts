import type { WAFRule, WAFResult } from "./types";

/**
 * Simulated WAF rules
 * In production, these would be configured in Cloudflare Dashboard
 */
const WAF_RULES: WAFRule[] = [
  {
    id: "block-sql-injection",
    description: "Block common SQL injection patterns",
    action: "block",
    conditions: [
      // UNION-based attacks (union combined with select)
      {
        field: "query",
        operator: "contains",
        value: "union select",
      },
      {
        field: "query",
        operator: "contains",
        value: "union all select",
      },
      {
        field: "query",
        operator: "contains",
        value: "union distinct",
      },
      // OR-based attacks with quotes
      {
        field: "query",
        operator: "contains",
        value: "' or '",
      },
      {
        field: "query",
        operator: "contains",
        value: "\" or \"",
      },
      {
        field: "query",
        operator: "contains",
        value: "or 1=1",
      },
      {
        field: "query",
        operator: "contains",
        value: "or '1'='1",
      },
      {
        field: "query",
        operator: "contains",
        value: "' or 1=1",
      },
      // DROP/DELETE attacks
      {
        field: "query",
        operator: "contains",
        value: "drop table",
      },
      {
        field: "query",
        operator: "contains",
        value: "drop database",
      },
      {
        field: "query",
        operator: "contains",
        value: "; drop",
      },
      {
        field: "query",
        operator: "contains",
        value: "'; drop",
      },
      // DELETE/INSERT/UPDATE in suspicious contexts
      {
        field: "query",
        operator: "contains",
        value: "delete from",
      },
      {
        field: "query",
        operator: "contains",
        value: "insert into",
      },
      {
        field: "query",
        operator: "contains",
        value: "; update",
      },
      {
        field: "query",
        operator: "contains",
        value: "'; update",
      },
      // Execute/Exec commands
      {
        field: "query",
        operator: "contains",
        value: "exec(",
      },
      {
        field: "query",
        operator: "contains",
        value: "execute(",
      },
      {
        field: "query",
        operator: "contains",
        value: "sp_executesql",
      },
      // SQL Comments
      {
        field: "query",
        operator: "contains",
        value: "--",
      },
      {
        field: "query",
        operator: "contains",
        value: "/*",
      },
      {
        field: "query",
        operator: "contains",
        value: "*/",
      },
      // SELECT with FROM (common in injection)
      {
        field: "query",
        operator: "contains",
        value: "select * from",
      },
      {
        field: "query",
        operator: "contains",
        value: "select from",
      },
      // Body checks for POST requests
      {
        field: "body",
        operator: "contains",
        value: "union select",
      },
      {
        field: "body",
        operator: "contains",
        value: "' or '",
      },
      {
        field: "body",
        operator: "contains",
        value: "or 1=1",
      },
      {
        field: "body",
        operator: "contains",
        value: "drop table",
      },
      {
        field: "body",
        operator: "contains",
        value: "delete from",
      },
      {
        field: "body",
        operator: "contains",
        value: "exec(",
      },
    ],
  },
  {
    id: "block-xss",
    description: "Block cross-site scripting attempts",
    action: "block",
    conditions: [
      // Script tags
      {
        field: "query",
        operator: "contains",
        value: "<script",
      },
      {
        field: "query",
        operator: "contains",
        value: "</script>",
      },
      // JavaScript protocols
      {
        field: "query",
        operator: "contains",
        value: "javascript:",
      },
      {
        field: "query",
        operator: "contains",
        value: "vbscript:",
      },
      // Event handlers
      {
        field: "query",
        operator: "contains",
        value: "onerror=",
      },
      {
        field: "query",
        operator: "contains",
        value: "onload=",
      },
      {
        field: "query",
        operator: "contains",
        value: "onclick=",
      },
      {
        field: "query",
        operator: "contains",
        value: "onmouseover=",
      },
      {
        field: "query",
        operator: "contains",
        value: "onfocus=",
      },
      // HTML tags that can execute scripts
      {
        field: "query",
        operator: "contains",
        value: "<img",
      },
      {
        field: "query",
        operator: "contains",
        value: "<iframe",
      },
      {
        field: "query",
        operator: "contains",
        value: "<object",
      },
      {
        field: "query",
        operator: "contains",
        value: "<embed",
      },
      // Alert patterns
      {
        field: "query",
        operator: "contains",
        value: "alert(",
      },
      // Body checks
      {
        field: "body",
        operator: "contains",
        value: "<script",
      },
      {
        field: "body",
        operator: "contains",
        value: "javascript:",
      },
      {
        field: "body",
        operator: "contains",
        value: "onerror=",
      },
      {
        field: "body",
        operator: "contains",
        value: "onclick=",
      },
      {
        field: "body",
        operator: "contains",
        value: "<img",
      },
      {
        field: "body",
        operator: "contains",
        value: "alert(",
      },
    ],
  },
  {
    id: "block-path-traversal",
    description: "Block path traversal attempts",
    action: "block",
    conditions: [
      {
        field: "path",
        operator: "contains",
        value: "../",
      },
      {
        field: "path",
        operator: "contains",
        value: "..\\",
      },
      {
        field: "query",
        operator: "contains",
        value: "../",
      },
      {
        field: "query",
        operator: "contains",
        value: "..\\",
      },
      {
        field: "query",
        operator: "contains",
        value: "/etc/",
      },
      {
        field: "query",
        operator: "contains",
        value: "c:\\",
      },
    ],
  },
  {
    id: "challenge-suspicious-ua",
    description: "Challenge requests with suspicious user agents",
    action: "challenge",
    conditions: [
      {
        field: "user-agent",
        operator: "contains",
        value: "bot",
      },
      {
        field: "user-agent",
        operator: "contains",
        value: "crawler",
      },
      {
        field: "user-agent",
        operator: "equals",
        value: "",
      },
    ],
  },
  {
    id: "block-admin-unauthorized",
    description: "Block access to admin paths without proper authentication",
    action: "block",
    conditions: [
      {
        field: "path",
        operator: "starts-with",
        value: "/admin",
      },
    ],
  },
];

/**
 * URL-encode a string for pattern matching
 */
function urlEncodePattern(pattern: string): string {
  try {
    return encodeURIComponent(pattern);
  } catch {
    return pattern;
  }
}

/**
 * Get all query parameter values (both raw and decoded) for WAF checking
 */
function getAllQueryValues(url: URL): string[] {
  const values: string[] = [];
  
  // Add raw query string (without the ?) - this includes URL-encoded values
  if (url.search) {
    const rawQuery = url.search.substring(1); // Remove leading ?
    values.push(rawQuery);
    
    // Also try to decode the entire raw query string
    try {
      const decodedQuery = decodeURIComponent(rawQuery);
      if (decodedQuery !== rawQuery) {
        values.push(decodedQuery);
      }
    } catch {
      // If decoding fails, raw query is already added
    }
  }
  
  // Add each query parameter value (searchParams automatically decodes)
  url.searchParams.forEach((paramValue, paramKey) => {
    // Add the decoded parameter value (searchParams already decodes URL encoding)
    values.push(paramValue);
    
    // Also check the parameter name itself (in case attack is in the key)
    values.push(paramKey);
  });
  
  return values;
}

/**
 * Evaluate a WAF condition
 */
function evaluateCondition(
  condition: WAFRule["conditions"][0],
  request: Request,
  url: URL,
  bodyText: string = ""
): boolean {
  let fieldValues: string[] = [];

  switch (condition.field) {
    case "path":
      fieldValues = [url.pathname];
      break;
    case "query":
      // Get all query parameter values (raw and decoded)
      fieldValues = getAllQueryValues(url);
      break;
    case "body":
      // Check request body
      if (bodyText) {
        fieldValues.push(bodyText);
        // Also try to parse as JSON and check stringified values
        try {
          const json = JSON.parse(bodyText);
          const stringified = JSON.stringify(json);
          fieldValues.push(stringified);
          // Also check individual values if it's an object
          if (typeof json === "object" && json !== null) {
            Object.values(json).forEach((val) => {
              if (typeof val === "string") {
                fieldValues.push(val);
              } else {
                fieldValues.push(String(val));
              }
            });
          }
        } catch {
          // Not JSON, bodyText is already added
        }
      }
      break;
    case "user-agent":
      fieldValues = [request.headers.get("User-Agent") || ""];
      break;
    case "host":
      fieldValues = [url.hostname];
      break;
    default:
      return false;
  }

  const value = condition.value.toLowerCase();
  // Also check URL-encoded version of the pattern
  const encodedValue = urlEncodePattern(condition.value).toLowerCase();

  // Check all field values against the condition
  return fieldValues.some((fieldValue) => {
    const normalizedValue = fieldValue.toLowerCase();

    switch (condition.operator) {
      case "equals":
        return normalizedValue === value || normalizedValue === encodedValue;
      case "contains":
        // Check both decoded and encoded patterns
        return normalizedValue.includes(value) || normalizedValue.includes(encodedValue);
      case "starts-with":
        return normalizedValue.startsWith(value) || normalizedValue.startsWith(encodedValue);
      case "ends-with":
        return normalizedValue.endsWith(value) || normalizedValue.endsWith(encodedValue);
      case "regex":
        try {
          return new RegExp(value).test(normalizedValue) || new RegExp(encodedValue).test(normalizedValue);
        } catch {
          return false;
        }
      default:
        return false;
    }
  });
}

/**
 * Get request body as string for WAF checking
 * Note: This clones the request to avoid consuming the body
 */
async function getRequestBody(request: Request): Promise<string> {
  // Only check body for methods that typically have bodies
  if (!["POST", "PUT", "PATCH"].includes(request.method)) {
    return "";
  }

  try {
    // Clone request to read body without consuming it
    const clonedRequest = request.clone();
    const contentType = clonedRequest.headers.get("Content-Type") || "";
    
    // Only process JSON and form data
    if (contentType.includes("application/json") || contentType.includes("application/x-www-form-urlencoded") || contentType.includes("text/")) {
      const body = await clonedRequest.text();
      return body;
    }
  } catch (error) {
    // If we can't read the body, continue without it
    console.warn("[WAF] Could not read request body:", error);
  }

  return "";
}

/**
 * Check if request matches any WAF rules
 */
export async function checkWAF(request: Request): Promise<WAFResult> {
  const startTime = performance.now();
  const url = new URL(request.url);
  let rulesEvaluated = 0;
  
  // Get request body for checking (async)
  const bodyText = await getRequestBody(request);

  // Debug logging (can be disabled in production)
  const debugEnabled = false; // Set to true for debugging
  if (debugEnabled && url.search) {
    console.log(`[WAF DEBUG] Checking URL: ${url.pathname}${url.search}`);
    console.log(`[WAF DEBUG] Query values:`, getAllQueryValues(url));
  }

  for (const rule of WAF_RULES) {
    rulesEvaluated++;
    // Check if any condition matches (OR logic)
    const matched = rule.conditions.some((condition) =>
      evaluateCondition(condition, request, url, bodyText)
    );

    if (matched) {
      // Rule matched, return action
      if (rule.action === "block") {
        console.log(`[WAF] Blocked request: ${rule.id} - ${url.pathname}${url.search}`);
        return {
          blocked: true,
          rule,
          reason: rule.description,
          timing: {
            checkDuration: performance.now() - startTime,
            rulesEvaluated,
          },
        };
      }

      if (rule.action === "challenge") {
        // In a real implementation, this would trigger Turnstile
        // For now, we just log it
        console.log(`[WAF] Challenge triggered: ${rule.description}`);
      }
    }
  }

  // No rules matched, allow request
  return {
    blocked: false,
    timing: {
      checkDuration: performance.now() - startTime,
      rulesEvaluated,
    },
  };
}

/**
 * Create a WAF block response
 */
export function createWAFBlockResponse(result: WAFResult): Response {
  return new Response(
    JSON.stringify({
      success: false,
      error: "Request blocked by WAF",
      rule: result.rule?.id,
      reason: result.reason,
    }),
    {
      status: 403,
      headers: {
        "Content-Type": "application/json",
        "X-WAF-Block": "true",
        "X-WAF-Rule": result.rule?.id || "unknown",
      },
    }
  );
}

/**
 * Get all WAF rules (for debugging/monitoring)
 */
export function getWAFRules(): WAFRule[] {
  return WAF_RULES;
}

/**
 * Add a custom WAF rule (runtime configuration)
 */
export function addWAFRule(rule: WAFRule): void {
  WAF_RULES.push(rule);
}
