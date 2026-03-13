const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;

export default async function handler(req, res) {
  if (req.method !== 'GET') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  const { session_id } = req.query;

  if (!session_id) {
    return res.status(400).json({ error: 'Missing session_id' });
  }

  try {
    const response = await fetch(
      `${SUPABASE_URL}/rest/v1/activation_codes?stripe_session_id=eq.${encodeURIComponent(session_id)}&select=code,pass_type,expires_at`,
      {
        headers: {
          'apikey': SUPABASE_SERVICE_ROLE_KEY,
          'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
        },
      }
    );

    if (!response.ok) {
      return res.status(500).json({ error: 'Database error' });
    }

    const rows = await response.json();

    if (!rows || rows.length === 0) {
      return res.status(404).json({ error: 'not_found' });
    }

    const row = rows[0];
    return res.status(200).json({
      code: row.code,
      passType: row.pass_type,
      expiresAt: row.expires_at,
    });
  } catch (err) {
    console.error('Activation lookup error:', err);
    return res.status(500).json({ error: 'Internal error' });
  }
}
