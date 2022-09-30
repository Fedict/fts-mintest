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
        signType: 'Legacy',
        names: [],
        xslt: '',
        out: 'out.pdf',
        outPathPrefix: 'signed_',
        profiles: [],
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
        selectDocuments: false,

        profilesForInputs: this.profilePerType.pdf,
        inputFiles: [],
        inputFileExts: [],
        pspFiles: [],
        xsltFiles: [],
        reasonForNoSubmit: null
        };

    this.handleSubmit = this.handleSubmit.bind(this);
    this.handleChange = this.handleChange.bind(this);
  }

//--------------------------------------------------------------------------

  hasFileExts(exts, all) {
    return this.state.inputFileExts.find((ext) => {
         index = exts.indexOf(ext);
         if (index >= 0) {
            if (!all || exts.length == 1) return true;
            exts.splice(index, 1);
        }
         return false;
      })
  }

//--------------------------------------------------------------------------

  handleChange(event) {
    target = event.target;
    value = target.value;
    if (target.type === 'checkbox') value = target.checked;
    else if (target.type === 'select-multiple') {
        value = Array.from(target.selectedOptions, (item) => item.value)
    }
    this.setState({ [target.id]: value }, this.adaptToChanges(target.id, value));
  }

//--------------------------------------------------------------------------

  checkProfile(fileType, profiles) {
     if (!this.hasFileExts([fileType])) return null;
     if (this.profilePerType[fileType].filter(n => (profiles.indexOf(n) !== -1)).length !== 0) return null;

     return 'A file of type "' + fileType + '" was selected but no Profile matching it was selected';
   }

//--------------------------------------------------------------------------

  adaptToChanges(targetId, value) {

    names = targetId === 'names' ? value : this.state.names;
    signType = targetId === 'signType' ? value : this.state.signType;
    profiles = targetId === 'profiles' ? value : this.state.profiles;
    psfN = targetId === 'psfN' ? value : this.state.psfN;
    out = targetId === 'out' ? value : this.state.out;

    if (signType == 'Legacy' && value.length != 1) {
        if (targetId === 'names') signType = 'Standard'
        else if (targetId === 'signType') names = [ names[0] ]
    }

    this.state.inputFileExts = [];
    names.forEach((name) => {
        ext = name.split(".").pop().toLowerCase();
        if (this.state.inputFileExts.indexOf(ext) < 0) this.state.inputFileExts.push(ext);
     })

    if (signType !== 'XadesMultiFile') {
        profilesForInputs = [];
        this.state.inputFileExts.forEach((ext) => { profilesForInputs = profilesForInputs.concat(this.profilePerType[ext]) })

        if (profilesForInputs.toString() !== this.state.profilesForInputs.toString()) profiles = [ profilesForInputs[0] ];
        maxProfiles = names.length === 1 ? 1 : 2;
        if (profiles.length > maxProfiles) {
            profiles.length = maxProfiles;
        }
    } else profiles = profilesForInputs = [ 'MDOC_XADES_LTA' ];

    if (signType !== 'Standard' || names.length === 1) {
       outFileExt = signType === 'XadesMultiFile' ? "xml" : (!names[0] ? "pdf" : names[0].split(".").pop().toLowerCase());

       if (out.indexOf('.') >= 0) {
           bitsOut = out.split('.');
           bitsOut.pop();
           bitsOut.push(outFileExt);
       } else if (out === '') out = 'out';

       out = bitsOut.join(".");
    } else out = '';

    if (targetId === 'names') {
        acroName = this.extractAcroformName(names[0]);
        if (acroName !== '') psfN = acroName
    }

    reasonForNoSubmit = null;
    if (!reasonForNoSubmit && (names.length === 1 || signType === 'XadesMultiFile') && out.length < 5) {
         reasonForNoSubmit = "The output file name must be > 5 character";
    }

    if (signType === 'Legacy') {
        if (!reasonForNoSubmit && this.hasFileExts(['pdf'])) {
            psfC = targetId === 'psfC' ? value : this.state.psfC;
            if (psfC) {
                if (! /^[0-9]+,[0-9]+,[0-9]+,[0-9]+,[0-9]+$/.test(psfC)) {
                    reasonForNoSubmit = "the 'PDF signature field coordinates' (PDF page#,x,y,width,height) must look like '3,10,10,30,30'";
                }
            } else {
                if (!psfN) {
                    reasonForNoSubmit = "you must provide either a 'PDF signature field name' or 'PDF signature field coordinates'";
                }
            }
        if (reasonForNoSubmit) reasonForNoSubmit = "For PDF input file " + reasonForNoSubmit;
        }
    }
    if (signType === 'Standard') {
        reasonForNoSubmit = this.checkProfile('pdf', profiles);
        if (reasonForNoSubmit == null) reasonForNoSubmit = this.checkProfile('xml', profiles);
        if (reasonForNoSubmit == null) {
            outPathPrefix = targetId === 'outPathPrefix' ? value : this.state.outPathPrefix;
            if (names.length > 1 && outPathPrefix === '') reasonForNoSubmit = 'Output prefix can\'t be empty for \'Standard\' multifile';
        }
    }

    this.setState( {
        names: names,
        signType: signType,
        profilesForInputs: profilesForInputs,
        profiles: profiles,
        out: out,
        psfN: psfN,
        reasonForNoSubmit: reasonForNoSubmit
    } )
  }

//--------------------------------------------------------------------------

  extractAcroformName(inFileName) {
    if (inFileName) {
        start = inFileName.indexOf('~');
        if (start >= 0) {
            end = inFileName.indexOf('.', ++start);
            if (end >= 0) return inFileName.substring(start, end);
        }
    }
    return '';
  }

//--------------------------------------------------------------------------

    getPdfParams(src, dst, legacy) {
        if (src.psp) dst[legacy ? 'psp' : 'pspFilePath'] = src.psp;
        if (src.lang) dst[legacy ? 'lang' : 'signLanguage'] = src.lang;
        if (src.psfN) dst.psfN = src.psfN;
        if (src.psfP) dst.psfP = src.psfP;
        if (src.psfC) dst.psfC = src.psfC.replaceAll(",", "%2C");
}

//--------------------------------------------------------------------------

  handleSubmit(event) {
    cleanState = Object.assign({}, this.state);

    if (!cleanState.allowedToSign) delete cleanState.allowedToSign;
    if (!cleanState.signTimeout) delete cleanState.signTimeout;

    if (cleanState.signType === 'Legacy') {
        urlParams = {
             in: names[0],
             out: cleanState.out,
             prof: cleanState.profiles[0],
             signTimeout: cleanState.signTimeout,
             noDownload: cleanState.noDownload,
             requestDocumentReadConfirm: cleanState.requestDocumentReadConfirm
        };
        if (cleanState.allowedToSign) {
            urlParams.allowedToSign = cleanState.allowedToSign.split(",").map(natNum => ({ nn: natNum }));
        }
        if (cleanState.xslt) urlParams.xslt = cleanState.xslt;

        extension = names[0].split('.').pop().toLowerCase()
        if (extension == "pdf") {
            this.getPdfParams(cleanState, urlParams, true);
        } else if (extension == "xml") {
            if (cleanState.xslt) urlParams.xslt = cleanState.xslt;
            if (cleanState.policyId) {
                urlParams.policyId = cleanState.policyId.replaceAll(":", "%3A");
                urlParams.policyDescription = cleanState.policyDescription;
                urlParams.policyDigestAlgorithm = cleanState.policyDigestAlgorithm;
            }
        }
    } else {
        urlParams = {
             signType: cleanState.signType,
             signTimeout: cleanState.signTimeout,
             requestDocumentReadConfirm: cleanState.requestDocumentReadConfirm,
             selectDocuments: cleanState.selectDocuments,
             outDownload: cleanState.noDownload === false,
             signProfile: cleanState.profiles[0],
             altSignProfile: cleanState.profiles[1]
        };
        if (cleanState.allowedToSign) urlParams.nnAllowedToSign = cleanState.allowedToSign.split(",");

        if (cleanState.previewDocuments && cleanState.signType === 'Standard' && cleanState.names.length !== 1) {
             urlParams.previewDocuments = cleanState.previewDocuments;
        }
        if (cleanState.signType === 'XadesMultiFile' || cleanState.names.length === 1) {
             urlParams.outFilePath = cleanState.out;
        } else {
             urlParams.outPathPrefix = cleanState.outPathPrefix;
        }
        if (cleanState.signType === 'XadesMultiFile' && cleanState.xslt) urlParams.outXsltPath = cleanState.xslt;

        if (cleanState.policyId) {
            urlParams.policy = { id : cleanState.policyId.replaceAll(":", "%3A"), description: cleanState.policyDescription, digestAlgorithm: cleanState.policyDigestAlgorithm };
        }

        count = 0;
        urlParams.inputs = cleanState.names.map(name => (cleanState.signType === 'XadesMultiFile' ? { filePath: name, xmlEltId: 'ID'+count++ } : { filePath: name } ));

        if (cleanState.names.length === 1) {
            if (cleanState.names[0].endsWith("pdf")) {
                this.getPdfParams(cleanState, urlParams.inputs[0]);
            } else if (cleanState.names[0].endsWith("xml")) {
                if (cleanState.xslt) urlParams.inputs[0].displayXsltPath = cleanState.xslt;
           }
        }
    }


    url = JSON.stringify(urlParams);
    url = url.replaceAll("{", "@o").replaceAll("}", "@c").replaceAll("[", "@O").replaceAll("]", "@C").replaceAll(",", "@S").replaceAll('"', "'").replaceAll(":", "@s");
    window.location="sign?json=" + url;
    event.preventDefault();
  }

//--------------------------------------------------------------------------

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
          ext = selectedFilename.split(".").pop().toLowerCase();
          inputFileExts = [ ext ];
          bitsOut = this.state.out.toLowerCase().split(".");
          bitsOut[bitsOut.length - 1] = ext;
          out = bitsOut.join("."),

          this.setState( {
                pspFiles: pspFiles,
                psp: pspFiles[0],
                xsltFiles: xsltFiles,
                xslt: xsltFiles[0],
                inputFiles: inputFiles,
                names: [selectedFilename],
                profilesForInputType: this.profilePerType[ext],
                profiles: [ this.profilePerType[ext][0] ],
                out: out,
                psfN: this.extractAcroformName(this.state.signType, [selectedFilename])
          })
      });
  }

  render() {

    singlePdf = false;
    singleXML = false;
    names = this.state.names;
    if (names && names.length == 1) {
        name = names[0].toLowerCase();
        singlePdf = name.endsWith('.pdf');
        singleXML = name.endsWith('.xml');
    }
    const hasPolicy = singleXML && this.state.policyId;
    return (
      <form onSubmit={this.handleSubmit}>
          <table style={{ border: '1px solid', width: '600px' }}>
            <tbody>
                <tr><td colSpan="2"><b>General parameters</b></td></tr>
                <tr><td><label>Signing type :</label></td>
                <td><select id="signType" value={this.state.signType} onChange={this.handleChange}>
                         <option>Legacy</option>
                         <option>Standard</option>
                         <option>XadesMultiFile</option>
                </select></td></tr>

                <tr><td><label>Input file name :</label></td>
                <td><select style={{ height: '200px' }} id="names" multiple={true} value={this.state.names} onChange={this.handleChange}>
                                    {this.state.inputFiles.map((inputFile) => <option key={inputFile}>{inputFile}</option>)}
                </select></td></tr>

                <tr><td><label>Output file name:</label></td>
                <td><input id="out" type="text" value={this.state.out} onChange={this.handleChange} disabled={this.state.signType === 'Standard' && this.state.names.length > 1}/></td></tr>

                <tr><td><label>Output prefix:</label></td>
                <td><input id="outPathPrefix" type="text" value={this.state.outPathPrefix} onChange={this.handleChange} disabled={this.state.signType !== 'Standard'}/></td></tr>

                <tr><td><label>Signing profile :</label></td>
                <td><select id="profiles" value={this.state.profiles} onChange={this.handleChange} multiple={true}>
                                        {this.state.profilesForInputs.map((profile) => <option key={profile}>{profile}</option>)}
                </select></td></tr>

                <tr><td><label>NN Allowed to Sign (Comma separated): </label></td>
                <td><input id="allowedToSign" type="text" value={this.state.allowedToSign} onChange={this.handleChange}/></td></tr>
                <tr><td><label>Sign timeout (in seconds)</label></td><td><input id="signTimeout" type="text" value={this.state.signTimeout} onChange={this.handleChange}/></td></tr>
                <tr><td><label>Disable output file download</label></td><td><input id="noDownload" type="checkbox" value={this.state.noDownload} onChange={this.handleChange}/></td></tr>
                <tr><td><label>Request read confirmation</label></td><td><input id="requestDocumentReadConfirm" type="checkbox" value={this.state.requestDocumentReadConfirm} onChange={this.handleChange}/></td></tr>
                <tr><td><label>Preview documents</label></td><td><input id="previewDocuments" disabled={this.state.signType !== 'XadesMultiFile'} type="checkbox" value={this.state.previewDocuments} onChange={this.handleChange}/></td></tr>
                <tr><td><label>Select documents</label></td><td><input id="selectDocuments" disabled={this.state.signType !== 'Standard' || this.state.names.length === 1} type="checkbox" value={this.state.selectDocuments} onChange={this.handleChange}/></td></tr>

                <tr><td colSpan="2"><hr/></td></tr>
                <tr><td colSpan="2"><b>PDF parameters</b></td></tr>
                <tr><td><label>PDF signature parameters file name: </label></td>
                <td><select id="psp" type="text" value={this.state.psp} disabled={!singlePdf} onChange={this.handleChange}>
                                        {this.state.pspFiles.map((pspFile) => <option key={pspFile}>{pspFile}</option>)}
                </select></td></tr>
                <tr><td><label>Language of the signature (Acroform): </label></td>
                <td><select id="lang" value={this.state.lang} onChange={this.handleChange}  disabled={!singlePdf}>
                                        <option value="de">Deutsch</option>
                                        <option value="en">English</option>
                                        <option value="fr">Français</option>
                                        <option value="nl">Nerderlands</option>
                </select></td></tr>

                <tr><td><label>PDF signature field name: </label></td>
                <td><input id="psfN" type="text" value={this.state.psfN} disabled={!singlePdf} onChange={this.handleChange}/></td></tr>

                <tr><td><label>PDF signature field coordinates: </label></td>
                <td><input id="psfC" type="text" value={this.state.psfC} disabled={!singlePdf} onChange={this.handleChange}/></td></tr>

                <tr><td><label>Include eID photo as icon in the PDF signature field</label></td><td><input id="psfP" type="checkbox" value={this.state.psfP} disabled={!singlePdf}  onChange={this.handleChange}/></td>
                </tr>
                <tr><td colSpan="2"><hr/></td></tr>
                <tr><td colSpan="2"><b>Non PDF parameters</b></td></tr>
                <tr><td><label>XSLT file name:</label></td>
                <td><select id="xslt" value={this.state.xslt} disabled={ !singleXML && this.state.signType !== 'XadesMultiFile' } onChange={this.handleChange}>
                                        {this.state.xsltFiles.map((xsltFile) => <option key={xsltFile}>{xsltFile}</option>)}
                </select></td></tr>
                <tr><td><label>Policy Id:</label></td>
                <td><select id="policyId" value={this.state.policyId} disabled={!singleXML} onChange={this.handleChange}>
                                        <option></option>
                                        <option>http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/Notary/BE_Justice_Signature_Policy_Notary_eID_Hum_v0.10_202109_Fr.pdf</option>
                                        <option>http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/PrivateSeal/BE_Justice_Signature_Policy_PrivateSeal_Hum_v0.5_201512_Nl.pdf</option>
                                        <option>http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/PrivateSeal/BE_Justice_Signature_Policy_PrivateSeal_Hum_v0.11_202111_Fr.pdf</option>
                </select></td></tr>

                <tr><td><label>Policy description (Optional):</label></td>
                <td><input id="policyDescription" type="text" value={this.state.policyDescription} onChange={this.handleChange} disabled={ !hasPolicy } /></td></tr>

                <tr><td><label>Policy Digest Algorithm :</label></td>
                <td><select id="policyDigestAlgorithm" value={this.state.policyDigestAlgorithm} onChange={this.handleChange} disabled={ !hasPolicy } >
                                        {this.policyDigestAlgorithms.map((algo) => <option key={algo}>{algo}</option>)}
                </select></td></tr>
                <tr><td colSpan="2"><hr/></td></tr>
                <tr><td colSpan="2"><input type="submit" value="Submit" disabled={this.state.reasonForNoSubmit}/>
                { this.state.reasonForNoSubmit && <p><label style={{ color: 'red' }}>Submit is disabled because : { this.state.reasonForNoSubmit }</label></p> }
                { this.state.signType === 'XadesMultiFile' && <p><label>This will produce a XADES Multifile signature.<br/>Policies are allowed, XSLT will be used to produce a custom output XML format</label></p> }
                { this.state.signType === 'Legacy' && <p><label>Sign a single file with basic options</label></p> }
                { this.state.signType === 'Standard' && <p><label>Sign 1 to n files with advanced options</label></p> }
                </td></tr>
            </tbody>
          </table>
      </form>
    );
  }
}
