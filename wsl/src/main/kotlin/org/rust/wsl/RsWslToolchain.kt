/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.util.io.exists
import com.intellij.util.io.isFile
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.RsToolchainProvider
import org.rust.stdext.toPath
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object RsWslToolchainProvider : RsToolchainProvider {
    override fun isApplicable(homePath: String): Boolean =
        homePath.startsWith(WSLDistribution.UNC_PREFIX)

    override fun getToolchain(homePath: String, toolchainName: String?): RsToolchain? {
        val (wslPath, distribution) = parseUncPath(homePath) ?: return null
        return RsWslToolchain(wslPath.toPath(), toolchainName, distribution)
    }
}

class RsWslToolchain(
    location: Path,
    name: String?,
    private val distribution: WSLDistribution
) : RsToolchain(location, name) {
    override val fileSeparator: String = "/"

    override fun <T : GeneralCommandLine> patchCommandLine(commandLine: T): T {
        for (group in commandLine.parametersList.paramsGroups) {
            val params = group.parameters.toList()
            group.parametersList.clearAll()
            group.parametersList.addAll(params.map { toRemotePath(it) })
        }

        commandLine.environment.forEach { (k, v) ->
            val paths = v.split(File.pathSeparatorChar)
            commandLine.environment[k] = paths.joinToString(":") { toRemotePath(it) }
        }

        commandLine.workDirectory?.let {
            if (it.path.startsWith(fileSeparator)) {
                commandLine.workDirectory = File(toLocalPath(it.path))
            }
        }

        val remoteWorkDir = commandLine.workDirectory?.toString()?.let { toRemotePath(it) }
        return distribution.patchCommandLine(commandLine, null, remoteWorkDir, false)
    }

    override fun startProcess(commandLine: GeneralCommandLine): ProcessHandler = RsWslProcessHandler(commandLine)

    override fun toLocalPath(remotePath: String): String = distribution.getWindowsPath(remotePath) ?: remotePath

    override fun toRemotePath(localPath: String): String = distribution.getWslPath(localPath) ?: localPath

    override fun expandUserHome(remotePath: String): String = distribution.expandUserHome(remotePath)

    override fun getExecutableName(toolName: String): String = toolName

    override fun pathToExecutable(toolName: String): Path {
        val uncLocation = distribution.toUncPath(location.toString())
        val exeName = getExecutableName(toolName)
        return Paths.get(uncLocation, exeName).toAbsolutePath()
    }

    override fun pathToCargoExecutable(toolName: String): Path {
        val exePath = pathToExecutable(toolName)
        if (exePath.exists()) return exePath
        val cargoBin = expandUserHome("~/.cargo/bin")
        val toUncPath = distribution.toUncPath(cargoBin)
        val exeName = getExecutableName(toolName)
        return Paths.get(toUncPath, exeName).toAbsolutePath()
    }

    override fun hasExecutable(exec: String): Boolean = pathToExecutable(exec).isFile()

    override fun hasCargoExecutable(exec: String): Boolean = pathToCargoExecutable(exec).isFile()
}
