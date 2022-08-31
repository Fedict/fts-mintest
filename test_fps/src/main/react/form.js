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
        name: [],
        xslt: '',
        out: 'out.pdf',
        prof: 'PADES_1',
        psp: '',
        psfN: 'signature_1',
        psfC: '1,30,20,180,60',
        psfP: false,
        lang: 'en',
        noDownload: false,
        signTimeout: '',
        allowedToSign: '',
        policyId: '',
        policyId: '',
        policyDescription: 'Policy Description',
        policyDigestAlgorithm: 'SHA512',
        requestDocumentReadConfirm: false,
        previewDocuments: true,

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
    if (this.isXadesMultifile()) return "xml";
    return this.state.name[0].toLowerCase().split(".").pop();
  }

  isXadesMultifile() {
    return this.state.name.length != 1;
  }

  handleChange(event) {
    target = event.target;
    value = target.value;
    if (target.type === 'checkbox') value = target.checked;
    else if (target.type === 'select-multiple') {
        value = Array.from(target.selectedOptions, (item) => item.value)
    }
    this.setState({ [target.id]: value }, this.adaptToChanges);
  }

  adaptToChanges() {
    reasonForNoSubmit = null;
    if (!reasonForNoSubmit && this.state.out.length < 5) {
         reasonForNoSubmit = "The output file name must be > 5 character (was : '" + this.state.out + "')";
    }
    if (!reasonForNoSubmit && this.inFileExt() == 'pdf') {
        if (this.state.psfC) {
            if (! /^[0-9]+,[0-9]+,[0-9]+,[0-9]+,[0-9]+$/.test(this.state.psfC)) {
                reasonForNoSubmit = "the 'PDF signature field coordinates' (PDF page#,x,y,width,height) must look like '3,10,10,30,30' (was : '" + this.state.psfC + "')";
            }
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

    inFileExt = this.inFileExt();
    bitsOut = this.state.out.toLowerCase().split(".");
    outFileExt = bitsOut.pop();
    if (inFileExt != outFileExt) {
        bitsOut.push(inFileExt);
        this.setState( {
            out: bitsOut.join("."),
            profilesForInputType: this.profilePerType[inFileExt],
            prof: this.profilePerType[inFileExt][0]
        });
    }

   this.setState( {
       psfN: this.extractAcroformName()
       });
  }

    extractAcroformName() {
        if (!this.isXadesMultifile()) {
            inFileName = this.state.name[0];
            start = inFileName.indexOf('~');
            if (start >= 0) {
                end = inFileName.indexOf('.', ++start);
                if (end >= 0) return inFileName.substring(start, end);
            }
        }
        return '';
    }

  handleSubmit(event) {
    cleanState = Object.assign({}, this.state);
    // Cleanup transient state
    delete cleanState.profilesForInputType;
    delete cleanState.inputFiles;
    delete cleanState.pspFiles;
    delete cleanState.xsltFiles;
    delete cleanState.reasonForNoSubmit;

    // Cleanup state based on file type
    extension = this.inFileExt();
    if (extension == "pdf") {
        delete cleanState.xslt;
        delete cleanState.policyId;
    } else if (extension == "xml") {
        delete cleanState.psp;
        delete cleanState.psfC;
        delete cleanState.psfN;
        delete cleanState.psfP;
    }

    if (!cleanState.policyId) {
        delete cleanState.policyDescription;
        delete cleanState.policyDigestAlgorithm;
    } else cleanState.policyId = cleanState.policyId.replaceAll(":", "%3A")

    if (!cleanState.allowedToSign) delete cleanState.allowedToSign;
    if (!cleanState.signTimeout) delete cleanState.signTimeout;
    if (!cleanState.psp) delete cleanState.psp;
    if (!cleanState.psfN) delete cleanState.psfN;
    if (!cleanState.psfC) delete cleanState.psfC;
    else cleanState.psfC = cleanState.psfC.replaceAll(",", "%2C")

    if (cleanState.name.length == 1) {
        cleanState.in = cleanState.name[0];
        delete cleanState.name;
        delete cleanState.previewDocuments;
        if (cleanState.allowedToSign) {
            cleanState.allowedToSign = cleanState.allowedToSign.split(",").map(natNum => ({ nn: natNum }));
        }
    } else {
        mDoc = {};
        mDoc.signTimeout = cleanState.signTimeout;
        mDoc.requestDocumentReadConfirm = cleanState.requestDocumentReadConfirm;
        mDoc.previewDocuments = cleanState.previewDocuments;
        if (cleanState.allowedToSign) mDoc.nnAllowedToSign = cleanState.allowedToSign.split(",");
        mDoc.signProfile = cleanState.prof;
        if (cleanState.policyId) {
            mDoc.policy = { id : cleanState.policyId, description: cleanState.policyDescription, digestAlgorithm: cleanState.policyDigestAlgorithm };
        }
        mDoc.outFilePath = cleanState.out;
        mDoc.outDownload = cleanState.noDownload === false;
        if (cleanState.xslt) mDoc.outXsltPath = cleanState.xslt;
        count = 0;
        mDoc.inputs = cleanState.name.map(name => ( { filePath: name, xmlEltId: 'ID'+count++ } ));

        cleanState = mDoc;
    }

    url = JSON.stringify(cleanState);
    url = url.replaceAll("{", "@o").replaceAll("}", "@c").replaceAll("[", "@O").replaceAll("]", "@C").replaceAll(",", "@S").replaceAll('"', "'").replaceAll(":", "@s");
    window.location="sign?json=" + url;
    event.preventDefault();
  }

  OLDhandleSubmit(event) {
    cleanState = Object.assign({}, this.state);

    // Cleanup transient state
    cleanState.profilesForInputType = cleanState.inputFiles = cleanState.pspFiles = cleanState.xsltFiles = null;

    // Cleanup state based on file type
    extension = this.inFileExt();
    if (extension == "pdf") {
        cleanState.xslt = null;
        cleanState.policyId = null;
    } else if (extension == "xml") {
        cleanState.psp = cleanState.psfC = cleanState.psfN = cleanState.psfP = null;
    }

    if (!cleanState.policyId) {
        cleanState.policyDescription = cleanState.policyDigestAlgorithm = null;
    }

    params = this.serialize(cleanState);
    window.location="sign?" + params;
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

          selectedFilename = inputFiles[0]
          ext = selectedFilename.toLowerCase().split(".").pop();
          bitsOut = this.state.out.toLowerCase().split(".");
          bitsOut[bitsOut.length - 1] = ext;
          out = bitsOut.join("."),

          this.setState( {
                pspFiles: pspFiles,
                psp: pspFiles[0],
                xsltFiles: xsltFiles,
                xslt: xsltFiles[0],
                inputFiles: inputFiles,
                name: [selectedFilename],
                profilesForInputType: this.profilePerType[ext],
                prof: this.profilePerType[ext][0],
                out: out,
                psfN: this.extractAcroformName()
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
                <td><select id="name" multiple={true} value={this.state.name} onChange={this.handleChange}>
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
                <tr><td><label>Sign timeout (in seconds)</label></td><td><input id="signTimeout" type="text" value={this.state.signTimeout} onChange={this.handleChange}/></td></tr>
                <tr><td><label>Disable output file download</label></td><td><input id="noDownload" type="checkbox" value={this.state.noDownload} onChange={this.handleChange}/></td></tr>
                <tr><td><label>Request read confirmation</label></td><td><input id="requestDocumentReadConfirm" type="checkbox" value={this.state.requestDocumentReadConfirm} onChange={this.handleChange}/></td></tr>
                <tr><td><label>Preview documents</label></td><td><input id="previewDocuments" type="checkbox" value={this.state.previewDocuments} onChange={this.handleChange}/></td></tr>

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
                                        <option>http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/PrivateSeal/BE_Justice_Signature_Policy_PrivateSeal_Hum_v0.5_201512_Nl.pdf</option>
                                        <option>http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/PrivateSeal/BE_Justice_Signature_Policy_PrivateSeal_Hum_v0.11_202111_Fr.pdf</option>
                </select></td></tr>

                <tr><td><label>Policiy description (Optional):</label></td>
                <td><input id="policyDescription" type="text" value={this.state.policyDescription} onChange={this.handleChange} disabled={this.inFileExt() == 'pdf' || !this.state.policyId} /></td></tr>

                <tr><td><label>Policy Digest Algorithm :</label></td>
                <td><select id="policyDigestAlgorithm" value={this.state.policyDigestAlgorithm} onChange={this.handleChange} disabled={this.inFileExt() == 'pdf' || !this.state.policyId } >
                                        {this.policyDigestAlgorithms.map((algo) => <option key={algo}>{algo}</option>)}
                </select></td></tr>
                <tr><td colSpan="2"><hr/></td></tr>
                <tr><td colSpan="2"><input type="submit" value="Submit" disabled={this.state.reasonForNoSubmit}/>
                { this.state.reasonForNoSubmit && <p><label style={{ color: 'red' }}>Submit is disabled because : { this.state.reasonForNoSubmit }</label></p> }
                { this.isXadesMultifile() && <p><label>This will produce a XADES Multifile signature.<br/>Policies are allowed, XSLT will be used to produce a custom output XML format</label></p> }
                </td></tr>
            </tbody>
          </table>
      </form>
    );
  }
}
