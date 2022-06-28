package jp.panta.misskeyandroidclient.ui.notes.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.withContext
import net.pantasystem.milktea.common_compose.CustomEmojiText
import net.pantasystem.milktea.common_compose.getSimpleElapsedTime
import net.pantasystem.milktea.model.notes.NoteCaptureAPIAdapter
import net.pantasystem.milktea.model.notes.NoteRelation

@ExperimentalCoroutinesApi
@Composable
@Stable
fun ItemRenoteUser(
    note: NoteRelation,
    onClick: ()->Unit,
    noteCaptureAPIAdapter: NoteCaptureAPIAdapter?,
    isUserNameDefault: Boolean = false
) {

    val createdAt = getSimpleElapsedTime(time = note.note.createdAt)

    LaunchedEffect(key1 = true){
        withContext(Dispatchers.IO) {
            noteCaptureAPIAdapter?.capture(note.note.id)?.launchIn(this)
        }
    }

    Card(
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier
            .padding(0.5.dp)
            .clickable {
                onClick.invoke()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically

        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Image(
                    painter = rememberAsyncImagePainter(note.user.avatarUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    if(isUserNameDefault){
                        CustomEmojiText(text = note.user.displayUserName, emojis = note.user.emojis)
                    }else{
                        CustomEmojiText(text = note.user.displayName, emojis = note.user.emojis)
                    }
                    if(isUserNameDefault){
                        Text(text = note.user.displayName)
                    }else{
                        Text(text = note.user.displayUserName)
                    }
                }


            }
            Text(createdAt)

        }
    }
}


