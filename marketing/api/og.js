import { ImageResponse } from '@vercel/og';

export const config = {
  runtime: 'edge',
};

export default function handler(req) {
  const { searchParams } = new URL(req.url);
  const title = searchParams.get('title') || 'No pierdas a tu grupo. Ni en el estadio.';
  const subtitle = searchParams.get('subtitle') || 'Chat sin internet por Bluetooth y WiFi mesh';
  const tag = searchParams.get('tag') || '';

  return new ImageResponse(
    {
      type: 'div',
      props: {
        style: {
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          padding: '60px 70px',
          background: 'linear-gradient(135deg, #0a2e1a 0%, #145a32 50%, #1a7a42 100%)',
          fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
        },
        children: [
          // Top row: logo + tag
          {
            type: 'div',
            props: {
              style: {
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              },
              children: [
                // Logo
                {
                  type: 'div',
                  props: {
                    style: {
                      display: 'flex',
                      fontSize: '42px',
                      fontWeight: 900,
                      letterSpacing: '-1px',
                    },
                    children: [
                      {
                        type: 'span',
                        props: {
                          style: { color: '#ffffff' },
                          children: 'Conect',
                        },
                      },
                      {
                        type: 'span',
                        props: {
                          style: { color: '#f4d03f' },
                          children: 'x',
                        },
                      },
                    ],
                  },
                },
                // Tag badge
                tag ? {
                  type: 'div',
                  props: {
                    style: {
                      background: 'rgba(244, 208, 63, 0.15)',
                      border: '1px solid rgba(244, 208, 63, 0.3)',
                      borderRadius: '20px',
                      padding: '6px 18px',
                      fontSize: '16px',
                      fontWeight: 700,
                      color: '#f4d03f',
                      letterSpacing: '0.5px',
                      textTransform: 'uppercase',
                    },
                    children: tag,
                  },
                } : {
                  type: 'div',
                  props: { children: '' },
                },
              ],
            },
          },
          // Middle: title + subtitle
          {
            type: 'div',
            props: {
              style: {
                display: 'flex',
                flexDirection: 'column',
                gap: '16px',
              },
              children: [
                {
                  type: 'div',
                  props: {
                    style: {
                      fontSize: '52px',
                      fontWeight: 900,
                      color: '#ffffff',
                      lineHeight: 1.15,
                      letterSpacing: '-1.5px',
                      maxWidth: '900px',
                    },
                    children: title,
                  },
                },
                {
                  type: 'div',
                  props: {
                    style: {
                      fontSize: '24px',
                      fontWeight: 400,
                      color: 'rgba(255, 255, 255, 0.7)',
                      lineHeight: 1.4,
                      maxWidth: '800px',
                    },
                    children: subtitle,
                  },
                },
              ],
            },
          },
          // Bottom row: badges
          {
            type: 'div',
            props: {
              style: {
                display: 'flex',
                gap: '16px',
                alignItems: 'center',
              },
              children: [
                // Bluetooth badge
                {
                  type: 'div',
                  props: {
                    style: {
                      background: 'rgba(255, 255, 255, 0.08)',
                      border: '1px solid rgba(255, 255, 255, 0.15)',
                      borderRadius: '20px',
                      padding: '8px 20px',
                      fontSize: '15px',
                      fontWeight: 600,
                      color: 'rgba(255, 255, 255, 0.7)',
                    },
                    children: 'Bluetooth',
                  },
                },
                // WiFi Mesh badge
                {
                  type: 'div',
                  props: {
                    style: {
                      background: 'rgba(255, 255, 255, 0.08)',
                      border: '1px solid rgba(255, 255, 255, 0.15)',
                      borderRadius: '20px',
                      padding: '8px 20px',
                      fontSize: '15px',
                      fontWeight: 600,
                      color: 'rgba(255, 255, 255, 0.7)',
                    },
                    children: 'WiFi Mesh',
                  },
                },
                // Offline badge
                {
                  type: 'div',
                  props: {
                    style: {
                      background: 'rgba(255, 255, 255, 0.08)',
                      border: '1px solid rgba(255, 255, 255, 0.15)',
                      borderRadius: '20px',
                      padding: '8px 20px',
                      fontSize: '15px',
                      fontWeight: 600,
                      color: 'rgba(255, 255, 255, 0.7)',
                    },
                    children: '100% Offline',
                  },
                },
                // Spacer
                {
                  type: 'div',
                  props: { style: { flex: 1 }, children: '' },
                },
                // Domain
                {
                  type: 'div',
                  props: {
                    style: {
                      fontSize: '18px',
                      fontWeight: 600,
                      color: 'rgba(244, 208, 63, 0.6)',
                    },
                    children: 'conectx.app · Mundial 2026',
                  },
                },
              ],
            },
          },
        ],
      },
    },
    {
      width: 1200,
      height: 630,
    }
  );
}
