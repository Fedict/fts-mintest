class NameForm extends React.Component {
  constructor(props) {
    super(props);

    this.getTestFileNames();

    this.policyDigestAlgorithms = [ "SHA1","SHA224","SHA256","SHA384","SHA512",
                    "SHA3_224","SHA3_256","SHA3_384","SHA3_512","SHAKE128","SHAKE256",
                    "SHAKE256_512","RIPEMD160","MD2","MD5","WHIRLPOOL","Dummy for out of range test"];

    this.profilePerType = {
        pdf: ["PADES_1", "PADES_LTA", "PADES_LTA_EXP_ALLOW"],
        xml: ["XADES_1", "XADES_2", "XADES_LTA", "XADES_LTA_DABS", "XADES_TA_EXP_ALLOW", "XADES_LTA_PTTSS"],
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
        allowedToSign: [],
        policyId: '',
        policyDescription: 'Policy Description',
        policyDigestAlgorithm: 'SHA512',
        requestDocumentReadConfirm: false,
        profilesForInputType: this.profilePerType.pdf,
        inputFiles: [],
        pspFiles: [],
        xsltFiles: []
        };

    this.handleSubmit = this.handleSubmit.bind(this);
    this.handleChange = this.handleChange.bind(this);
    this.handleChangeName = this.handleChangeName.bind(this);

//    this.forceUpdate();
  }

  hasPolicy() {
     return this.state.policyId !=null && this.state.policyId.trim().length != 0;
  }

  inFileExt() {
    bits = this.state.name.toLowerCase().split(".");
    return bits[bits.length - 1];
  }

  handleChange(event) {
    target = event.target;
    this.setState({ [target.id]: target.type === 'checkbox' ? target.checked : target.value });
  }

  handleChangeName(event) {
    target = event.target;
    this.setState({ [target.id]: target.value }, this.adaptToNameChanges);
  }

  adaptToNameChanges() {
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

    cleanState.profilesForInputType = cleanState.inputFiles = cleanState.pspFiles = cleanState.xsltFiles = null;

    lowerName = cleanState.name.toLowerCase();
    if (lowerName.endsWith(".pdf")) {
        cleanState.xslt = null;
        cleanState.policyId = null;
    } else if (lowerName.endsWith(".xml")) {
        cleanState.psp = null;
    }

    if (cleanState.policyId == null || cleanState.policyId.length == 0) {
        cleanState.policyDescription = cleanState.policyDigestAlgorithm = null;
    }

    window.location="sign?" + this.serialize(cleanState);
    event.preventDefault();
  }

  serialize(obj) {
    var str = [];
    for (var p in obj)
        if (obj.hasOwnProperty(p) && obj[p] != null && obj[p].length != 0) {
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
          this.setState( {
                pspFiles: pspFiles,
                psp: pspFiles[0],
                xsltFiles: xsltFiles,
                xslt: xsltFiles[0],
                inputFiles: inputFiles,
                name: inputFiles[0]
          })
      });
  }

  render() {
    return (
      <form onSubmit={this.handleSubmit}>
        <ul>
            <li>
                <label>Input file name :</label>
                <select id="name" value={this.state.name} onChange={this.handleChangeName}>
                    {this.state.inputFiles.map((inputFile) => <option key={inputFile}>{inputFile}</option>)}
                </select>
            </li>
            <li>
                <label>XSLT file name:</label>
                <select id="xslt" value={this.state.xslt} disabled={this.inFileExt() != 'xml'} onChange={this.handleChange}>
                    {this.state.xsltFiles.map((xsltFile) => <option key={xsltFile}>{xsltFile}</option>)}
                </select>
            </li>
            <li>
                <label>Output file name:</label>
                <input id="out" type="text" value={this.state.out} onChange={this.handleChangeName}/>
            </li>
            <li>
                <label>Signing profile :</label>
                <select id="prof" value={this.state.prof} onChange={this.handleChange}>
                    {this.state.profilesForInputType.map((profile) => <option key={profile}>{profile}</option>)}
                </select>
            </li>
            <li>
                <label>PDF signature parameters file name: </label>
                <select id="psp" type="text" value={this.state.psp} disabled={this.inFileExt() != 'pdf'} onChange={this.handleChange}>
                    {this.state.pspFiles.map((pspFile) => <option key={pspFile}>{pspFile}</option>)}
                </select>
            </li>
            <li>
                <label>PDF signature field name: </label>
                <input id="psfN" type="text" value={this.state.psfN} disabled={this.inFileExt() != 'pdf'}  onChange={this.handleChange}/>
            </li>
            <li>
                <label>PDF signature field coordinates: </label>
                <input id="psfC" type="text" value={this.state.psfC} disabled={this.inFileExt() != 'pdf'}  onChange={this.handleChange}/>
            </li>
            <li>
                <label><input id="psfP" type="checkbox" value={this.state.psfP} disabled={this.inFileExt() != 'pdf'}  onChange={this.handleChange}/> Include eID photo as icon in the PDF signature field</label>
            </li>
            <li>
                <label>Language: </label>
                <select id="lang" value={this.state.lang} onChange={this.handleChange}>
                    <option value="de">Deutsch</option>
                    <option value="en">English</option>
                    <option value="fr">Français</option>
                    <option value="nl">Nerderlands</option>
                </select>
            </li>
            <li>
                <label>Allowed to Sign: </label>
                <input id="allowedToSign" type="text" value={this.state.allowedToSign} onChange={this.handleChange}/>
            </li>
            <li>
                <label><input id="noDownload" type="checkbox" value={this.state.noDownload} onChange={this.handleChange}/> Allow output file download</label>
            </li>
            <li>
                <label><input id="requestDocumentReadConfirm" type="checkbox" value={this.state.requestDocumentReadConfirm} onChange={this.handleChange}/> Request read confirmation</label>
            </li>
            <li>
                <label>Policy Id:</label>
                <select id="policyId" value={this.state.policyId} disabled={this.inFileExt() == 'pdf'} onChange={this.handleChange}>
                    <option></option>
                    <option>http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/Notary/BE_Justice_Signature_Policy_Notary_eID_Hum_v0.10_202109_Fr.pdf</option>
                </select>
            </li>
            <li>
                <label>Policiy description (Optional):</label>
                <input id="policyDescription" type="text" value={this.state.policyDescription} onChange={this.handleChange} disabled={!this.hasPolicy() || this.inFileExt() == 'pdf'} />
            </li>
            <li>
                <label>Policy Digest Algorithm :</label>
                <select id="policyDigestAlgorithm" value={this.state.policyDigestAlgorithm} onChange={this.handleChange} disabled={!this.hasPolicy() || this.inFileExt() == 'pdf'} >
                    {this.policyDigestAlgorithms.map((algo) => <option key={algo}>{algo}</option>)}
                </select>
            </li>
        </ul>
        <input type="submit" value="Submit" />
      </form>
    );
  }
}
