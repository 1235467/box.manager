package xyz.chz.bfm.util.command

import android.content.Context
import xyz.chz.bfm.util.magisk.MagiskHelper.execRootCmd
import xyz.chz.bfm.util.magisk.MagiskHelper.execRootCmdSilent
import xyz.chz.bfm.util.magisk.MagiskHelper.execRootCmdVoid
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

object TermCmd {
    val path = "/data/adb/box/"

    fun isProxying(): Boolean {
        return execRootCmdSilent("if [ -f ${path}run/box.pid ] ; then exit 1;fi") == 1
    }

    fun renewBox(callback: (Boolean) -> Unit) {
        thread {
            val cmd = "${path}scripts/box.iptables renew"
            execRootCmdVoid(cmd, callback)
        }
    }

    fun start(callback: (Boolean) -> Unit) {
        thread {
            val cmd = "${path}scripts/box.service start && ${path}scripts/box.iptables enable"
            execRootCmdVoid(cmd, callback)
        }
    }

    fun stop(callback: (Boolean) -> Unit) {
        thread {
            val cmd = "${path}scripts/box.iptables disable && ${path}scripts/box.service stop"
            execRootCmdVoid(cmd, callback)
        }
    }

    fun readLog(): String {
        return execRootCmd("cat ${path}run/runs.log")
    }

    val linkDBClash: String
        get() {
            return execRootCmd("grep 'external-controller:' ${path}clash/config.yaml | awk '{print $2}'")
        }


    val linkDBSing: String
        get() {
            val cmd =
                "grep -w 'external_controller' ${path}sing-box/config.json | awk '{print $2}' | sed 's/\"//g' | sed 's/,//g'"
            return execRootCmd(cmd)
        }

    val appidList: HashSet<String>
        get() {
            val s = HashSet<String>()
            val cmd =
                "grep 'packages_list' ${path}settings.ini | sed 's/^.*=//' | sed 's/(//g' | sed 's/)//g' | awk 'END{print}'"
            val result = execRootCmd(cmd)
            if ("" == result) {
                return s
            }
            val appIds = result.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (i in appIds) {
                s.add(i)
            }
            return s
        }

    private fun setAppIdList(): Boolean {
        return execRootCmdSilent("sed -i 's/packages_list=.*/packages_list=()/;' ${path}settings.ini") != -1
    }

    fun setAppidList(s: HashSet<String?>): Boolean {
        if (s.size == 0) {
            return setAppIdList()
        }
        val cmd = StringBuilder("sed -i 's/packages_list=.*/packages_list=( ")
        for (i in s) {
            cmd.append(i).append(" ")
        }
        cmd.append(")/;' ${path}settings.ini")
        return execRootCmdSilent(cmd.toString().trim { it <= ' ' }) != -1
    }

    private fun getNameConfig(what: String, isClash: Boolean): String {
        val m = if (isClash) "yaml" else "json"
        return execRootCmd("find ${path}$what/ -maxdepth 1 -name 'config.$m' -type f -printf '%f\n'")
    }

    fun getConfig(): String {
        val what = SettingCmd.core
        val isClash = what == "clash"
        val name = getNameConfig(what, isClash)
        return execRootCmd("cat ${path}${what}/${name}")
    }

    fun saveConfig(ctx: Context, str: String, callback: (Boolean) -> Unit) {
        thread {
            val what = SettingCmd.core
            val isClash = what == "clash"
            val name = getNameConfig(what, isClash)
            val exFile = File(ctx.getExternalFilesDir(null), "out.txt")
            val fos = FileOutputStream(exFile)
            fos.write(str.toByteArray())
            val cmd = "mv -f $exFile ${path}${what}/${name}"
            execRootCmdVoid(cmd, callback)
        }
    }

    private fun yqParser(dir: String, config: String, isClash: Boolean): String {
        val yq = "${path}bin/yq"
        return if (isClash) {
            val yamlToJson = "$yq -oj ${path}${dir}/${config} > ${path}${dir}/xtemp.json"
            execRootCmd("$yamlToJson && $yq -oy ${path}${dir}/xtemp.json > ${path}${dir}/${config} && rm -f ${path}${dir}/xtemp.json && cat ${path}${dir}/${config}")
        } else {
            execRootCmd("$yq -oj ${path}${dir}/${config} > ${path}${dir}/xtemp.json && mv -f ${path}${dir}/xtemp.json ${path}${dir}/${config} && cat ${path}${dir}/${config}")
        }
    }

}