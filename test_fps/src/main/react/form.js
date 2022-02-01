class NameForm extends React.Component {
  constructor(props) {
    super(props);

    this.getTestFileNames();

    this.policyDigestAlgorithms = [ "SHA1","SHA224","SHA256","SHA384","SHA512",
                    "SHA3_224","SHA3_256","SHA3_384","SHA3_512","SHAKE128","SHAKE256",
                    "SHAKE256_512","RIPEMD160","MD2","MD5","WHIRLPOOL","Dummy for out of range test"];

    this.profilePerType = {
        pdf: ["PADES_1", "PADES_LTA", "PADES_LTA_EXP_ALLOW"],
        xml: ["XADES_1", "XADES_2", "XADES_LT", "XADES_LTA"],
        bin: ["CADES_1", "CADES_2", "CADES_LTA", "CADES_LTA_ENVELOPING"]
    }

    this.state = {
        name: '',
        xslt: '',
        out: 'out.pdf',
        prof: 'PADES_1',
        psp: '',
        psfN: 'signature_1',
        psfC: '1,30,20,180,60',
        psfP: false,
        lang: 'en',
        noDownload: false,
        signTimeout: 120,
        allowedToSign: '',
        policyId: '',
        policyDescription: 'Policy Description',
        policyDigestAlgorithm: 'SHA512',
        requestDocumentReadConfirm: false,

        profilesForInputType: this.profilePerType.pdf,
        inputFiles: [],
        pspFiles: [],
        xsltFiles: [],
        reasonForNoSubmit: null
        };

    this.handleSubmit = this.handleSubmit.bind(this);
    this.handleChange = this.handleChange.bind(this);
  }

  inFileExt() {
    bits = this.state.name.toLowerCase().split(".");
    return bits[bits.length - 1];
  }

  handleChange(event) {
    target = event.target;
    this.setState({
        [target.id]: target.type === 'checkbox' ? target.checked : target.value }, this.adaptToChanges);
  }

  adaptToChanges() {
    reasonForNoSubmit = null;
    if (!reasonForNoSubmit && this.state.out.length < 5) {
         reasonForNoSubmit = "The output file name must be > 5 character (was : '" + this.state.out + "')";
    }
    if (!reasonForNoSubmit && this.inFileExt() == 'pdf') {
        if (this.state.psfC) {
            if (/^[0-9]+,[0-9]+,[0-9]+,[0-9]+,[0-9]+$/.test(this.state.psfC)) {
                coord = this.state.psfC.split(',');
                if (parseInt(coord[1]) >= parseInt(coord[3]) || parseInt(coord[2]) >= parseInt(coord[4])) {
                    reasonForNoSubmit = "the 'PDF signature field coordinates' must look like 'page#, top,left,bottom,right (was : '" + this.state.psfC + "')";
                }
            } else reasonForNoSubmit = "the 'PDF signature field coordinates' must look like '3, 10,10,30,30' (was : '" + this.state.psfC + "')";
        } else {
            if (!this.state.psfN) {
                reasonForNoSubmit = "you must provide either a 'PDF signature field name' or 'PDF signature field coordinates'";
            }
        }
    if (reasonForNoSubmit) reasonForNoSubmit = "For PDF input file " + reasonForNoSubmit;
    }
    if (reasonForNoSubmit != this.state.reasonForNoSubmit) {
        this.setState( { reasonForNoSubmit: reasonForNoSubmit } );
    }

    bitsName = this.state.name.toLowerCase().split(".");
    ext = bitsName[bitsName.length - 1];
    bitsOut = this.state.out.toLowerCase().split(".");
    if (ext != bitsOut[bitsOut.length - 1]) {
        bitsOut[bitsOut.length - 1] = ext;
        this.setState( {
            out: bitsOut.join("."),
            profilesForInputType: this.profilePerType[ext],
            prof: this.profilePerType[ext][0]
        });
    }
  }

  handleSubmit(event) {
    cleanState = Object.assign({}, this.state);

    // Cleanup transient state
    cleanState.profilesForInputType = cleanState.inputFiles = cleanState.pspFiles = cleanState.xsltFiles = null;

    // Cleanup state based on file type
    lowerName = cleanState.name.toLowerCase();
    if (lowerName.endsWith(".pdf")) {
        cleanState.lang = null;
        cleanState.xslt = null;
        cleanState.policyId = null;
    } else if (lowerName.endsWith(".xml")) {
        cleanState.psp = cleanState.psfC = cleanState.psfN = cleanState.psfP = null;
    }

    if (!cleanState.policyId) {
        cleanState.policyDescription = cleanState.policyDigestAlgorithm = null;
    }

    window.location="sign?" + this.serialize(cleanState);
    event.preventDefault();
  }

  serialize(obj) {
    var str = [];
    for (var p in obj)
        if (obj.hasOwnProperty(p) && obj[p]) {
          str.push(encodeURIComponent(p) + "=" + encodeURIComponent(obj[p]));
        }
    return str.join("&");
  }

   getTestFileNames() {
      fetch("/getFileList").then(response => response.text())
          .then((response) => {
              pspFiles = [ '' ];
              xsltFiles = [ '' ];
              inputFiles = [];

              var fileNames = response.split(",").sort();
              fileNames.forEach((fileName) => {
                  pos = fileName.lastIndexOf('.');
                  if (pos >= 0) {
                    ext = fileName.substring(pos + 1).toLowerCase();
                    if (ext == "pdf" || ext == "xml") inputFiles.push(fileName);
                    else if (ext == "psp") pspFiles.push(fileName);
                    else if (ext == "xslt") xsltFiles.push(fileName);
                  }
              });

          bitsName = inputFiles[0].toLowerCase().split(".");
          ext = bitsName[bitsName.length - 1];
          bitsOut = this.state.out.toLowerCase().split(".");
          bitsOut[bitsOut.length - 1] = ext;
          out = bitsOut.join("."),

          this.setState( {
                pspFiles: pspFiles,
                psp: pspFiles[0],
                xsltFiles: xsltFiles,
                xslt: xsltFiles[0],
                inputFiles: inputFiles,
                name: inputFiles[0],
                profilesForInputType: this.profilePerType[ext],
                prof: this.profilePerType[ext][0],
                out: out
          })
      });
  }

  render() {
    return (
      <form onSubmit={this.handleSubmit}>
          <table style={{ border: '1px solid', width: '600px' }}>
            <tbody>
                <tr><td colSpan="2"><b>General parameters</b></td></tr>
                <tr><td><label>Input file name :</label></td>
                <td><select id="name" value={this.state.name} onChange={this.handleChange}>
                                    {this.state.inputFiles.map((inputFile) => <option key={inputFile}>{inputFile}</option>)}
                </select></td></tr>

                <tr><td><label>Output file name:</label></td>
                <td><input id="out" type="text" value={this.state.out} onChange={this.handleChange}/></td></tr>

                <tr><td><label>Signing profile :</label></td>
                <td><select id="prof" value={this.state.prof} onChange={this.handleChange}>
                                        {this.state.profilesForInputType.map((profile) => <option key={profile}>{profile}</option>)}
                </select></td></tr>
                <tr><td><label>NN Allowed to Sign (Comma separated): </label></td>
                <td><input id="allowedToSign" type="text" value={this.state.allowedToSign} onChange={this.handleChange}/></td></tr>
                <tr><td><label>Sign timeout</label></td><td><input id="signTimeout" type="text" value={this.state.signTimeout} onChange={this.handleChange}/></td>
                </tr>
                <tr><td><label>Allow output file download</label></td><td><input id="noDownload" type="checkbox" value={this.state.noDownload} onChange={this.handleChange}/></td>
                </tr>
                <tr><td><label>Request read confirmation</label></td><td><input id="requestDocumentReadConfirm" type="checkbox" value={this.state.requestDocumentReadConfirm} onChange={this.handleChange}/></td>
                </tr>

                <tr><td colSpan="2"><hr/></td></tr>
                <tr><td colSpan="2"><b>PDF parameters</b></td></tr>
                <tr><td><label>PDF signature parameters file name: </label></td>
                <td><select id="psp" type="text" value={this.state.psp} disabled={this.inFileExt() != 'pdf'} onChange={this.handleChange}>
                                        {this.state.pspFiles.map((pspFile) => <option key={pspFile}>{pspFile}</option>)}
                </select></td></tr>
                <tr><td><label>Language of the signature (Acroform): </label></td>
                <td><select id="lang" value={this.state.lang} onChange={this.handleChange}  disabled={this.inFileExt() != 'pdf'}>
                                        <option value="de">Deutsch</option>
                                        <option value="en">English</option>
                                        <option value="fr">Français</option>
                                        <option value="nl">Nerderlands</option>
                </select></td></tr>

                <tr><td><label>PDF signature field name: </label></td>
                <td><input id="psfN" type="text" value={this.state.psfN} disabled={this.inFileExt() != 'pdf'} onChange={this.handleChange}/></td></tr>

                <tr><td><label>PDF signature field coordinates: </label></td>
                <td><input id="psfC" type="text" value={this.state.psfC} disabled={this.inFileExt() != 'pdf'} onChange={this.handleChange}/></td></tr>

                <tr><td><label>Include eID photo as icon in the PDF signature field</label></td><td><input id="psfP" type="checkbox" value={this.state.psfP} disabled={this.inFileExt() != 'pdf'}  onChange={this.handleChange}/></td>
                </tr>
                <tr><td colSpan="2"><hr/></td></tr>
                <tr><td colSpan="2"><b>Non PDF parameters</b></td></tr>
                <tr><td><label>XSLT file name:</label></td>
                <td><select id="xslt" value={this.state.xslt} disabled={this.inFileExt() != 'xml'} onChange={this.handleChange}>
                                        {this.state.xsltFiles.map((xsltFile) => <option key={xsltFile}>{xsltFile}</option>)}
                </select></td></tr>
                <tr><td><label>Policy Id:</label></td>
                <td><select id="policyId" value={this.state.policyId} disabled={this.inFileExt() == 'pdf'} onChange={this.handleChange}>
                                        <option></option>
                                        <option>http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/Notary/BE_Justice_Signature_Policy_Notary_eID_Hum_v0.10_202109_Fr.pdf</option>
                </select></td></tr>

                <tr><td><label>Policiy description (Optional):</label></td>
                <td><input id="policyDescription" type="text" value={this.state.policyDescription} onChange={this.handleChange} disabled={this.inFileExt() == 'pdf' || !this.state.policyId} /></td></tr>

                <tr><td><label>Policy Digest Algorithm :</label></td>
                <td><select id="policyDigestAlgorithm" value={this.state.policyDigestAlgorithm} onChange={this.handleChange} disabled={this.inFileExt() == 'pdf' || !this.state.policyId } >
                                        {this.policyDigestAlgorithms.map((algo) => <option key={algo}>{algo}</option>)}
                </select></td></tr>
                <tr><td colSpan="2"><hr/></td></tr>
                <tr><td colSpan="2"><input type="submit" value="Submit" disabled={this.state.reasonForNoSubmit}/>
                { this.state.reasonForNoSubmit && <label><br/>Submit is disabled because : { this.state.reasonForNoSubmit }</label> }
                </td></tr>
            </tbody>
          </table>
      </form>
    );
  }
}
