package com.musicbandhub.app.data

data class UserProfile(val id: String, val name: String, val instrument: String? = null)
data class Band(val id: String, val name: String, val description: String = "", val inviteCode: String = "")
data class BandMember(val userId: String, val bandId: String, val role: String)
