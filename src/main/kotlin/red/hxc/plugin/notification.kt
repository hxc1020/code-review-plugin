package red.hxc.plugin

import com.intellij.AbstractBundle
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.reference.SoftReference
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.util.*


private val GROUP = NotificationGroupManager.getInstance().getNotificationGroup(
    CodeReviewBundle.message("c.r.notification.group"),
)

fun showStartupNotification(project: Project) {
    val notification: Notification = GROUP.createNotification(
        CodeReviewBundle.message(
            "c.r.notification.group"
        ),
        CodeReviewBundle.message("c.r.notification.startup"),
        NotificationType.INFORMATION, null
    )
        .setIcon(IconLoader.getIcon("images/logo_dark.svg", CodeReviewBundle::class.java))
        .addAction(
            BrowseNotificationAction(
                CodeReviewBundle.message("c.r.notification.startup.link.name"),
                CodeReviewBundle.message("c.r.notification.startup.link")
            )
        )
    notification.notify(project)
}

fun showNotification(project: Project?, message: String) {
    if (project == null) return
    val notification: Notification = GROUP.createNotification(
        CodeReviewBundle.message(
            "c.r.notification.group"
        ),
        message,
        NotificationType.INFORMATION, null
    )
        .setIcon(IconLoader.getIcon("images/logo_dark.svg", CodeReviewBundle::class.java))
    notification.notify(project)
}

object CodeReviewBundle {
    private const val PATH_TO_BUNDLE = "messages.CodeReviewPluginBundle"
    private var ourBundle: Reference<ResourceBundle>? = null
    fun message(
        @PropertyKey(resourceBundle = "messages.CodeReviewPluginBundle") key: String?,
        vararg params: Any?
    ): String {
        return AbstractBundle.message(bundle!!, key!!, *params)
    }

    private val bundle: ResourceBundle?
        get() {
            var bundle = SoftReference.dereference(ourBundle)
            if (bundle == null) {
                bundle = ResourceBundle.getBundle(PATH_TO_BUNDLE)
                ourBundle = SoftReference(bundle)
            }
            return bundle
        }
}
