package com.example.data.local.db

import com.example.data.local.entity.ReminderOwnerType

data class PendingIntentIdentitySnapshot(
  val pendingIntentType: String = DEFAULT_PENDING_INTENT_TYPE,
  val requestCode: Int,
  val component: String,
  val packageName: String,
  val actionName: String = ACTION_FIRE_REMINDER,
  val dataUri: String,
  val mimeType: String? = null,
  val intentIdentifier: String? = null,
  val categoriesCanonical: String = "",
  val pendingIntentIdentityKey: String,
  val notificationTag: String,
  val notificationId: Int = DEFAULT_NOTIFICATION_ID,
) {
  companion object {
    const val DEFAULT_PENDING_INTENT_TYPE = "BROADCAST"
    const val ACTION_FIRE_REMINDER = "com.yawmi.productivity.action.FIRE_REMINDER"
    const val DEFAULT_RECEIVER_COMPONENT = "com.example.reminder.AlarmReceiver"
    const val DEFAULT_NOTIFICATION_ID = 0

    fun buildDataUri(ownerType: ReminderOwnerType, reminderId: String): String =
      "yawmi://reminder/${ownerType.name.lowercase()}/$reminderId"

    fun buildNotificationTag(ownerType: ReminderOwnerType, reminderId: String): String =
      "yawmi:reminder:${ownerType.name.lowercase()}:$reminderId"

    fun canonicalCategories(categories: Collection<String>): String =
      categories.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted().joinToString(",")

    fun buildKey(
      pendingIntentType: String = DEFAULT_PENDING_INTENT_TYPE,
      requestCode: Int,
      actionName: String,
      dataUri: String,
      mimeType: String? = null,
      intentIdentifier: String? = null,
      component: String,
      packageName: String,
      categoriesCanonical: String = "",
    ): String {
      return buildString {
        append("type=").append(pendingIntentType)
        append("|req=").append(requestCode)
        append("|act=").append(actionName)
        append("|data=").append(dataUri)
        append("|mime=").append(mimeType.orEmpty())
        append("|id=").append(intentIdentifier.orEmpty())
        append("|cmp=").append(component)
        append("|pkg=").append(packageName)
        append("|cats=").append(categoriesCanonical)
      }
    }

    fun forReminder(
      ownerType: ReminderOwnerType,
      reminderId: String,
      requestCode: Int,
      packageName: String,
      component: String = DEFAULT_RECEIVER_COMPONENT,
      actionName: String = ACTION_FIRE_REMINDER,
      mimeType: String? = null,
      intentIdentifier: String? = null,
      categories: Collection<String> = emptyList(),
    ): PendingIntentIdentitySnapshot {
      val dataUri = buildDataUri(ownerType, reminderId)
      val cats = canonicalCategories(categories)
      val key =
        buildKey(
          pendingIntentType = DEFAULT_PENDING_INTENT_TYPE,
          requestCode = requestCode,
          actionName = actionName,
          dataUri = dataUri,
          mimeType = mimeType,
          intentIdentifier = intentIdentifier,
          component = component,
          packageName = packageName,
          categoriesCanonical = cats,
        )
      return PendingIntentIdentitySnapshot(
        pendingIntentType = DEFAULT_PENDING_INTENT_TYPE,
        requestCode = requestCode,
        component = component,
        packageName = packageName,
        actionName = actionName,
        dataUri = dataUri,
        mimeType = mimeType,
        intentIdentifier = intentIdentifier,
        categoriesCanonical = cats,
        pendingIntentIdentityKey = key,
        notificationTag = buildNotificationTag(ownerType, reminderId),
        notificationId = DEFAULT_NOTIFICATION_ID,
      )
    }
  }
}
