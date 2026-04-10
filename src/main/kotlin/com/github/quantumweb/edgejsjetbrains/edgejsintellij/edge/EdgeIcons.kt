package com.github.quantumweb.edgejsjetbrains.edge

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.Icon

object EdgeIcons {
    private const val FILE_ICON_SIZE = 16

    @JvmField
    val FILE: Icon = loadPngIcon("/edge.png", FILE_ICON_SIZE)

    private fun loadPngIcon(path: String, size: Int): Icon {
        val sourceImage = EdgeIcons::class.java.getResourceAsStream(path)?.use(ImageIO::read)
            ?: error("Missing icon resource: $path")

        if (sourceImage.width == size && sourceImage.height == size) {
            return ImageIcon(sourceImage)
        }

        val scaledImage = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaledImage.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.drawImage(sourceImage, 0, 0, size, size, null)
        graphics.dispose()

        return ImageIcon(scaledImage)
    }
}
