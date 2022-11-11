package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResultImpl
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.implementation.R
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import javax.inject.Inject

class CommandSMBBolus(
    injector: HasAndroidInjector,
    private val detailedBolusInfo: DetailedBolusInfo,
    callback: Callback?
) : Command(injector, CommandType.SMB_BOLUS, callback) {

    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val r: PumpEnactResult
        val lastBolusTime = repository.getLastBolusRecord()?.timestamp ?: 0L
        aapsLogger.debug(LTag.PUMPQUEUE, "Last bolus: $lastBolusTime ${dateUtil.dateAndTimeAndSecondsString(lastBolusTime)}")
        if (lastBolusTime != 0L && lastBolusTime + T.mins(3).msecs() > dateUtil.now()) {
            aapsLogger.debug(LTag.PUMPQUEUE, "SMB requested but still in 3 min interval")
            r = PumpEnactResultImpl(injector).enacted(false).success(false).comment("SMB requested but still in 3 min interval")
        } else if (detailedBolusInfo.deliverAtTheLatest != 0L && detailedBolusInfo.deliverAtTheLatest + T.mins(1).msecs() > System.currentTimeMillis()) {
            r = activePlugin.activePump.deliverTreatment(detailedBolusInfo)
        } else {
            r = PumpEnactResultImpl(injector).enacted(false).success(false).comment("SMB request too old")
            aapsLogger.debug(LTag.PUMPQUEUE, "SMB bolus canceled. deliverAt: " + dateUtil.dateAndTimeString(detailedBolusInfo.deliverAtTheLatest))
        }
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
    }

    override fun status(): String = rh.gs(R.string.smb_bolus_u, detailedBolusInfo.insulin)

    override fun log(): String = "SMB BOLUS ${rh.gs(R.string.formatinsulinunits, detailedBolusInfo.insulin)}"
}