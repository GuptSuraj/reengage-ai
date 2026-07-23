# Five-minute demo

1. Start with `docker compose up --build`, then open `http://localhost:3000`.
2. Sign in as `demo@reengage.ai` / `Demo@12345`.
3. Search for wireless headphones, filter the price range, open Sony and JBL products, add one to the cart, and leave checkout incomplete. Mention that the reusable SDK batches each interaction.
4. Sign out and sign in as `admin@reengage.ai` / `Admin@12345`; open `/admin`.
5. Select **Simulate high-intent journey** to create a deterministic shortcut. Show the explained score, leading signals, recommendations, queue state, and live funnel.
6. After the local 15-second delay, show the mock provider attempt changing the notification to sent.
7. Repeat the journey but complete checkout before the due time. Show that the purchase transaction cancels the pending job and decrements stock.
8. Close with the outbox/Kafka path, delivery-time eligibility recheck, Redis repair sweep, Qdrant fallback, Prometheus/Grafana links, and CI checks.

Do not claim an LLM makes consent or discount decisions. Its only allowed future role is rewriting an already-approved message.
