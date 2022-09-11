package net.pantasystem.milktea.model.notes.renote

import kotlinx.coroutines.runBlocking
import net.pantasystem.milktea.model.account.Account
import net.pantasystem.milktea.model.notes.CreateNote
import net.pantasystem.milktea.model.notes.NoteRepository
import net.pantasystem.milktea.model.notes.Visibility
import net.pantasystem.milktea.model.notes.generateEmptyNote
import net.pantasystem.milktea.model.user.User
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking

class CreateRenoteUseCaseTest {

    private val account = Account(
        "testId",
        "misskey.io",
        instanceType = Account.InstanceType.MISSKEY,
        encryptedToken = "test",
        userName = "test",
        accountId = 0L,
        pages = emptyList(),
    )

    @Test
    fun invoke() {
        val target = generateEmptyNote().copy(
            visibility = Visibility.Public(true),
            text = "test"
        )
        val noteRepository = mock<NoteRepository> {
            onBlocking {
                create(any()).getOrThrow()
            } doReturn generateEmptyNote()
            onBlocking {
                find(any()).getOrThrow()
            } doReturn target
        }
        val useCase = CreateRenoteUseCase(
            getAccount = {
                account
            },
            noteRepository = noteRepository,
        )
        runBlocking {
            useCase.invoke(target.id)
        }
        verifyBlocking(noteRepository) {
            create(CreateNote(account, target.visibility, text = null, renoteId = target.id))
        }
    }

    @Test
    fun invoke_GiveIllegalRenote() {
        val target = generateEmptyNote().copy(
            visibility = Visibility.Specified(emptyList()),
            userId = User.Id(accountId = account.accountId, id = account.remoteId + "other"),
            text = "test"
        )

        val noteRepository = mock<NoteRepository> {
            onBlocking {
                create(any()).getOrThrow()
            } doReturn generateEmptyNote()
            onBlocking {
                find(any()).getOrThrow()
            } doReturn target
        }

        val useCase = CreateRenoteUseCase(
            getAccount = {
                account
            },
            noteRepository = noteRepository,
        )
        runBlocking {
            useCase.invoke(target.id).onFailure {
                Assert.assertTrue(it is IllegalArgumentException)
            }
        }


    }

}