<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
        <xsl:comment> Created By BosaSign 1.0 </xsl:comment>
        <just:SignedDoc xmlns:just="http://signinfo.eda.just.fgov.be/XSignInfo/2008/07/just#">
            <xsl:for-each select="root/file">
                <just:DataFile ContentType="EMBEDDED_BASE64" id="{@id}" FileName="{@name}" MimeType="pdf" Size="{@size}" />
            </xsl:for-each>
        </just:SignedDoc>
    </xsl:template>
</xsl:stylesheet>