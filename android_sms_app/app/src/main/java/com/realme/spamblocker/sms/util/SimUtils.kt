package com.realme.spamblocker.sms.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager

data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String
)

object SimUtils {

    @SuppressLint("MissingPermission")
    fun getAvailableSims(context: Context): List<SimInfo> {
        val simList = mutableListOf<SimInfo>()
        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                ?: return emptyList()

            val activeSubscriptions: List<SubscriptionInfo>? = subscriptionManager.activeSubscriptionInfoList
            if (activeSubscriptions != null) {
                for (info in activeSubscriptions) {
                    val slot = info.simSlotIndex
                    val carrier = info.carrierName?.toString() ?: "SIM ${slot + 1}"
                    val name = info.displayName?.toString() ?: carrier
                    simList.add(
                        SimInfo(
                            subscriptionId = info.subscriptionId,
                            slotIndex = slot,
                            displayName = name,
                            carrierName = carrier
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Permission not yet granted or single SIM fallback
        }

        // If no SIMs detected or permission restricted, return default SIM 1
        if (simList.isEmpty()) {
            simList.add(SimInfo(-1, 0, "SIM 1", "Default SIM"))
        }

        return simList
    }
}
