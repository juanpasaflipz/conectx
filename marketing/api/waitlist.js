const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') return res.status(200).end();
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

  const { email, platform } = req.body || {};

  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return res.status(400).json({ error: 'Email invalido' });
  }

  const row = { email: email.toLowerCase().trim() };
  if (platform && ['android', 'ios', 'desktop', 'other'].includes(platform)) {
    row.platform = platform;
  }

  try {
    const response = await fetch(`${SUPABASE_URL}/rest/v1/waitlist`, {
      method: 'POST',
      headers: {
        'apikey': SUPABASE_SERVICE_ROLE_KEY,
        'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
        'Content-Type': 'application/json',
        'Prefer': 'return=minimal',
      },
      body: JSON.stringify(row),
    });

    if (response.status === 409 || (response.status === 400 && (await response.text()).includes('duplicate'))) {
      return res.status(200).json({ ok: true, message: 'Ya estas en la lista' });
    }

    if (!response.ok) {
      const err = await response.text();
      // Handle unique constraint violation from PostgREST
      if (err.includes('duplicate') || err.includes('unique')) {
        return res.status(200).json({ ok: true, message: 'Ya estas en la lista' });
      }
      throw new Error(err);
    }

    return res.status(200).json({ ok: true, message: 'Te anotamos' });
  } catch (err) {
    console.error('Waitlist error:', err);
    return res.status(500).json({ error: 'Error interno' });
  }
}
