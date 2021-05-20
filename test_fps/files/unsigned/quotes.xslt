<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:template match="/">
  <html>
  <body>
    <h3>Quotes</h3>
    <table border="1">
      <tr>
        <th>Quote</th>
        <th>Author</th>
      </tr>
      <xsl:for-each select="quotes/quote">
        <tr>
          <td><xsl:value-of select="text" /></td>
          <td><xsl:value-of select="author" /></td>
        </tr>
      </xsl:for-each>
    </table>
  </body>
  </html>
</xsl:template>
</xsl:stylesheet>

