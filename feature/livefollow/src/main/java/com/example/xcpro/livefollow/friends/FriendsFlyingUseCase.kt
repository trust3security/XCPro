package com.example.xcpro.livefollow.friends

import com.example.xcpro.livefollow.data.friends.FriendsFlyingRepository
import javax.inject.Inject

class FriendsFlyingUseCase @Inject constructor(
    private val repository: FriendsFlyingRepository
) {
    val state = repository.state

    suspend fun refresh() {
        repository.refresh()
    }
}
