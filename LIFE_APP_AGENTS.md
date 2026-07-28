# Life App Subagents (Conductor OS)

Date: 2026-07-28

Conductor treats personal apps as **OS subagents**: granted, purpose-scoped, policy-gated, and operated through playbooks after login.

## Founding life surface matrix

| Domain | Example packages | Read / draft | Side-effect | Money |
|--------|------------------|--------------|-------------|-------|
| Messages | Google Messages | draft | send (exact approval) | — |
| Calendar | Google Calendar | agenda | tentative hold | — |
| Contacts | Google Contacts | lookup | call (exact approval) | — |
| Maps | Google Maps | place search | route; share location (exact) | — |
| Email | Gmail | draft | send (exact approval) | — |
| Shopping | Amazon, Walmart, Target | search, cart | purchase (exact approval) | yes |
| Banking | Chase, BofA, PayPal, Wallet | balances, cards | transfer/send (exact approval) | yes |
| Social | Facebook | — | public post (exact approval) | — |
| Browser | Chrome | open/search | no payments | — |
| Outdoor cross-app | calendar+weather+places | plan | draft invite | — |

## Hard safety rules

1. **Never automate passwords, MFA, or security setting changes.**
2. **Money-moving actions always require exact approval** of amount/destination/order summary — even in Trusted Auto.
3. Public posts and outbound messages require exact content approval.
4. Production UI ops queue through AccessibilityService (no false verified receipts).
5. Instant stop forces Ask Only and clears queues.

## Voice routing

`VoiceIntentClassifier` + `LifeDomainIntentRouter` map utterances to `life_*` intents, then `LifeDomainPlanner` emits multi-step plans that call app subagent action types.

Examples:

- “Check my bank balance” → `life_banking` → `banking.balance.read`
- “Transfer $50 to savings” → `life_banking` → `banking.transfer.create` (approval)
- “Search Amazon for headphones and add to cart” → `life_shopping` → search + cart
- “Buy those headphones” → `purchase.create` (exact approval)
- “Directions to the park” → `life_maps` → `maps.route.open`
- “Text Maya that I’m free at 3” → `life_messaging` → draft (+ send if asked)

## Teach path

Any additional app becomes a subagent via Accessibility discovery + teach-app dry-run + per-package grant.
