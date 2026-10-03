package com.example.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseService {
    private const val SUPABASE_URL = "https://vpgxvjyiiuiafbvhzsqq.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_ViSgIQa5g9ukT4dCnbjKLA_NWqhVz3u"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Auth) {
            scheme = "vortex"
            host = "login-callback"
        }
        install(Postgrest)
    }
}
