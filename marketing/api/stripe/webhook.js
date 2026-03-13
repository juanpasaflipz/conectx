const Stripe = require('stripe');
const crypto = require('crypto');

const stripe = new Stripe(process.env.STRIPE_SECRET_KEY);

const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;

const PRICE_MAP = {
  'price_1T9TkkFL0IK12LxSANUpn2Ws': 'mundial',
  'price_1T9TlTFL0IK12LxSIS7JP9Ud': 'partido',
};

const MUNDIAL_EXPIRY = new Date('2026-07-20T04:59:59Z');

function generateCode() {
  const bytes = crypto.randomBytes(12);
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 12; i++) {
    code += chars[bytes[i] % chars.length];
  }
  return code;
}

function getExpiry(passType) {
  if (passType === 'mundial') return MUNDIAL_EXPIRY.toISOString();
  const now = new Date();
  const cdtOffset = -5 * 60;
  const local = new Date(now.getTime() + cdtOffset * 60 * 1000);
  local.setHours(23, 59, 59, 999);
  const utc = new Date(local.getTime() - cdtOffset * 60 * 1000);
  return utc.toISOString();
}

async function insertActivationCode({ code, email, passType, expiresAt, sessionId, customerId }) {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/activation_codes`, {
    method: 'POST',
    headers: {
      'apikey': SUPABASE_SERVICE_ROLE_KEY,
      'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
      'Content-Type': 'application/json',
      'Prefer': 'return=representation',
    },
    body: JSON.stringify({
      code,
      email,
      pass_type: passType,
      expires_at: expiresAt,
      stripe_session_id: sessionId,
      stripe_customer_id: customerId,
    }),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(`Supabase insert failed: ${res.status} ${err}`);
  }
  return res.json();
}

module.exports = async function handler(req, res) {
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  try {
    const event = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;

    if (!event || !event.type) {
      return res.status(400).json({ error: 'Invalid payload' });
    }

    if (event.type === 'checkout.session.completed') {
      const session = event.data.object;
      const email = session.customer_details?.email || session.customer_email;

      if (!email) {
        return res.status(200).json({ received: true, warning: 'no email' });
      }

      // Determine pass type from payment_link ID
      const paymentLinkId = session.payment_link;
      let passType = null;

      // Map payment links to pass types
      if (paymentLinkId === 'plink_1T9UkWFL0IK12LxSgjGlCwh4') {
        passType = 'mundial';
      } else if (paymentLinkId === 'plink_1T9UllFL0IK12LxSZiGhHM9V') {
        passType = 'partido';
      }

      // Fallback: check amount
      if (!passType) {
        const amount = session.amount_total;
        if (amount === 19900) passType = 'mundial';
        else if (amount === 3900) passType = 'partido';
      }

      if (!passType) {
        return res.status(200).json({ received: true, warning: 'unknown pass type' });
      }

      const code = generateCode();
      const expiresAt = getExpiry(passType);

      await insertActivationCode({
        code,
        email,
        passType,
        expiresAt,
        sessionId: session.id,
        customerId: session.customer,
      });

      console.log(`Activation code created: ${code} (${passType}) for ${email}`);
    }

    if (event.type === 'charge.refunded') {
      const charge = event.data.object;
      const customerId = charge.customer;
      if (customerId) {
        await fetch(`${SUPABASE_URL}/rest/v1/activation_codes?stripe_customer_id=eq.${customerId}`, {
          method: 'PATCH',
          headers: {
            'apikey': SUPABASE_SERVICE_ROLE_KEY,
            'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ expires_at: new Date().toISOString() }),
        });
      }
    }

    return res.status(200).json({ received: true });
  } catch (err) {
    console.error('Webhook error:', err.message);
    return res.status(500).json({ error: err.message });
  }
};
