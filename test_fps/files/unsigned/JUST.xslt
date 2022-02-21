<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
        <xsl:comment> Created By BosaSign 1.0 </xsl:comment>
        <SignedDoc>
            <xsl:for-each select="root/file">
                <DataFile id="{@id}" FileName="{@name}"/>
            </xsl:for-each>
        </SignedDoc>
    </xsl:template>
</xsl:stylesheet>
