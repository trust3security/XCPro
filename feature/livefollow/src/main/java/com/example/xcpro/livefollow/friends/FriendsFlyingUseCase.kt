package com.example.xcpro.livefollow.friends

import com.example.xcpro.livefollow.account.XcAccountRepository
import com.example.xcpro.livefollow.data.following.FollowingLiveRepository
import com.example.xcpro.livefollow.data.friends.FriendsFlyingRepository
import javax.inject.Inject

class FriendsFlyingUseCase @Inject constructor(
    private val publicRepository: FriendsFlyingRepository,
    private val followingRepository: FollowingLiveRepository,
    private val accountRepository: XcAccountRepository
) {
    val publicState = publicRepository.state
    val followingState = followingRepository.state
    val accountState = accountRepository.state

    suspend fun refreshPublic() {
        publicRepository.refresh()
    }

    suspend fun refreshFollowing() {
        followingRepository.refresh()
    }
}
