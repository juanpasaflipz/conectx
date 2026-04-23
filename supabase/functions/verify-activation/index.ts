import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, apikey",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(JSON.stringify({ valid: false, message: "Method not allowed" }), {
      status: 405,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    const { code } = await req.json();

    if (!code || typeof code !== "string" || code.trim().length === 0) {
      return new Response(
        JSON.stringify({ valid: false, userId: "", passType: "", expiresAt: 0, message: "Codigo invalido" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const cleanCode = code.trim().toUpperCase().replace(/[^A-Z0-9]/g, "");

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    // Look up the activation code
    const { data, error } = await supabase
      .from("activation_codes")
      .select("*")
      .eq("code", cleanCode)
      .single();

    if (error || !data) {
      return new Response(
        JSON.stringify({ valid: false, userId: "", passType: "", expiresAt: 0, message: "Codigo no encontrado" }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Check if expired
    const expiresAtMs = new Date(data.expires_at).getTime();
    if (Date.now() >= expiresAtMs) {
      return new Response(
        JSON.stringify({ valid: false, userId: data.id, passType: data.pass_type, expiresAt: expiresAtMs, message: "Codigo expirado" }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Mark as redeemed if not already
    if (!data.redeemed) {
      await supabase
        .from("activation_codes")
        .update({ redeemed: true, redeemed_at: new Date().toISOString() })
        .eq("id", data.id);
    }

    return new Response(
      JSON.stringify({
        valid: true,
        userId: data.id,
        passType: data.pass_type,
        expiresAt: expiresAtMs,
        message: "",
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err) {
    console.error("verify-activation error:", err);
    return new Response(
      JSON.stringify({ valid: false, userId: "", passType: "", expiresAt: 0, message: "Error interno" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
