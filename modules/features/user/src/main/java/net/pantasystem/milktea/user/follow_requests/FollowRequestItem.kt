package net.pantasystem.milktea.user.follow_requests

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import net.pantasystem.milktea.common_compose.CustomEmojiText
import net.pantasystem.milktea.model.account.Account
import net.pantasystem.milktea.model.user.User
import net.pantasystem.milktea.user.R

@Composable
fun FollowRequestItem(
    currentAccount: Account?,
    user: User,
    isUserNameDefault: Boolean,
    onAccept: (User.Id) -> Unit,
    onReject: (User.Id) -> Unit,
    onAvatarClicked: (User.Id) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colors.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 12.dp,
                    horizontal = 14.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                rememberAsyncImagePainter(model = user.avatarUrl),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colors.surface, CircleShape)
                    .clickable {
                        onAvatarClicked(user.id)
                    },
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (isUserNameDefault) {
                    Text(text = user.displayUserName, fontSize = 16.sp)
                    CustomEmojiText(text = user.displayName, emojis = user.emojis, accountHost = currentAccount?.getHost(), sourceHost = user.host, fontSize = 14.sp)
                } else {
                    CustomEmojiText(text = user.displayName, emojis = user.emojis, accountHost = currentAccount?.getHost(), sourceHost = user.host, fontSize = 16.sp)
                    Text(text = user.displayUserName, fontSize = 14.sp)
                }

            }
            Spacer(modifier = Modifier.width(4.dp))
            Row {
                IconButton(onClick = {
                    onAccept(user.id)
                }) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(id = R.string.accept))
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = {
                    onReject(user.id)
                }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.reject))
                }
            }

        }
    }
}

@Preview
@Composable
fun Preview_FollowRequestItem() {
    FollowRequestItem(
        currentAccount = Account(remoteId = "", instanceDomain = "", userName = "", instanceType = Account.InstanceType.MISSKEY, token = ""),
        isUserNameDefault = true,
        user = User.Simple(
            id = User.Id(accountId = 0, id = ""),
            userName = "Panta",
            name = "Panta",
            avatarUrl = null,
            emojis = listOf(),
            isCat = null,
            isBot = null,
            host = "",
            nickname = null,
            isSameHost = false,
            instance = null,
            avatarBlurhash = null
        ), onAccept = {}, onReject = {}, onAvatarClicked = {}
    )
}