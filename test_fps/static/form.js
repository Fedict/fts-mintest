var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

var NameForm = function (_React$Component) {
  _inherits(NameForm, _React$Component);

  function NameForm(props) {
    _classCallCheck(this, NameForm);

    var _this = _possibleConstructorReturn(this, (NameForm.__proto__ || Object.getPrototypeOf(NameForm)).call(this, props));

    _this.getTestFileNames();

    _this.policyDigestAlgorithms = ["SHA1", "SHA224", "SHA256", "SHA384", "SHA512", "SHA3_224", "SHA3_256", "SHA3_384", "SHA3_512", "SHAKE128", "SHAKE256", "SHAKE256_512", "RIPEMD160", "MD2", "MD5", "WHIRLPOOL", "Dummy for out of range test"];

    _this.profilePerType = {
      pdf: ["PADES_1", "PADES_LTA", "PADES_LTA_EXP_ALLOW"],
      xml: ["XADES_1", "XADES_2", "XADES_LTA", "XADES_LTA_DABS", "XADES_TA_EXP_ALLOW", "XADES_LTA_PTTSS"],
      bin: ["CADES_1", "CADES_2", "CADES_LTA", "CADES_LTA_ENVELOPING"]
    };

    _this.state = {
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
      profilesForInputType: _this.profilePerType.pdf,
      inputFiles: [],
      pspFiles: [],
      xsltFiles: []
    };

    _this.handleSubmit = _this.handleSubmit.bind(_this);
    _this.handleChange = _this.handleChange.bind(_this);
    _this.handleChangeName = _this.handleChangeName.bind(_this);
    return _this;
  }

  _createClass(NameForm, [{
    key: "hasPolicy",
    value: function hasPolicy() {
      return this.state.policyId != null && this.state.policyId.trim().length != 0;
    }
  }, {
    key: "inFileExt",
    value: function inFileExt() {
      bits = this.state.name.toLowerCase().split(".");
      return bits[bits.length - 1];
    }
  }, {
    key: "handleChange",
    value: function handleChange(event) {
      target = event.target;
      this.setState(_defineProperty({}, target.id, target.type === 'checkbox' ? target.checked : target.value));
    }
  }, {
    key: "handleChangeName",
    value: function handleChangeName(event) {
      target = event.target;
      this.setState(_defineProperty({}, target.id, target.value), this.adaptToNameChanges);
    }
  }, {
    key: "adaptToNameChanges",
    value: function adaptToNameChanges() {
      bitsName = this.state.name.toLowerCase().split(".");
      ext = bitsName[bitsName.length - 1];
      bitsOut = this.state.out.toLowerCase().split(".");
      if (ext != bitsOut[bitsOut.length - 1]) {
        bitsOut[bitsOut.length - 1] = ext;
        this.setState({
          out: bitsOut.join("."),
          profilesForInputType: this.profilePerType[ext],
          prof: this.profilePerType[ext][0]
        });
      }
    }
  }, {
    key: "handleSubmit",
    value: function handleSubmit(event) {
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

      window.location = "sign?" + this.serialize(cleanState);
      event.preventDefault();
    }
  }, {
    key: "serialize",
    value: function serialize(obj) {
      var str = [];
      for (var p in obj) {
        if (obj.hasOwnProperty(p) && obj[p] != null && obj[p].length != 0) {
          str.push(encodeURIComponent(p) + "=" + encodeURIComponent(obj[p]));
        }
      }return str.join("&");
    }
  }, {
    key: "getTestFileNames",
    value: function getTestFileNames() {
      var _this2 = this;

      fetch("/getFileList").then(function (response) {
        return response.text();
      }).then(function (response) {
        pspFiles = [''];
        xsltFiles = [''];
        inputFiles = [];

        var fileNames = response.split(",").sort();
        fileNames.forEach(function (fileName) {
          pos = fileName.lastIndexOf('.');
          if (pos >= 0) {
            ext = fileName.substring(pos + 1).toLowerCase();
            if (ext == "pdf" || ext == "xml") inputFiles.push(fileName);else if (ext == "psp") pspFiles.push(fileName);else if (ext == "xslt") xsltFiles.push(fileName);
          }
        });
        _this2.setState({
          pspFiles: pspFiles,
          psp: pspFiles[0],
          xsltFiles: xsltFiles,
          xslt: xsltFiles[0],
          inputFiles: inputFiles,
          name: inputFiles[0]
        });
      });
    }
  }, {
    key: "render",
    value: function render() {
      return React.createElement(
        "form",
        { onSubmit: this.handleSubmit },
        React.createElement(
          "table",
          { style: { border: '1px solid', width: '600px' } },
          React.createElement(
            "tbody",
            null,
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                { colspan: "2" },
                React.createElement(
                  "b",
                  null,
                  "General parameters"
                )
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "Input file name :"
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement(
                  "select",
                  { id: "name", value: this.state.name, onChange: this.handleChangeName },
                  this.state.inputFiles.map(function (inputFile) {
                    return React.createElement(
                      "option",
                      { key: inputFile },
                      inputFile
                    );
                  })
                )
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "Output file name:"
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement("input", { id: "out", type: "text", value: this.state.out, onChange: this.handleChangeName })
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "Signing profile :"
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement(
                  "select",
                  { id: "prof", value: this.state.prof, onChange: this.handleChange },
                  this.state.profilesForInputType.map(function (profile) {
                    return React.createElement(
                      "option",
                      { key: profile },
                      profile
                    );
                  })
                )
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "Language: "
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement(
                  "select",
                  { id: "lang", value: this.state.lang, onChange: this.handleChange },
                  React.createElement(
                    "option",
                    { value: "de" },
                    "Deutsch"
                  ),
                  React.createElement(
                    "option",
                    { value: "en" },
                    "English"
                  ),
                  React.createElement(
                    "option",
                    { value: "fr" },
                    "Fran\xE7ais"
                  ),
                  React.createElement(
                    "option",
                    { value: "nl" },
                    "Nerderlands"
                  )
                )
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "NN Allowed to Sign (Comma separated): "
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement("input", { id: "allowedToSign", type: "text", value: this.state.allowedToSign, onChange: this.handleChange })
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "Allow output file download"
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement("input", { id: "noDownload", type: "checkbox", value: this.state.noDownload, onChange: this.handleChange })
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "Request read confirmation"
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement("input", { id: "requestDocumentReadConfirm", type: "checkbox", value: this.state.requestDocumentReadConfirm, onChange: this.handleChange })
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                { colspan: "2" },
                React.createElement("hr", null)
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                { colspan: "2" },
                React.createElement(
                  "b",
                  null,
                  "PDF parameters"
                )
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "PDF signature parameters file name: "
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement(
                  "select",
                  { id: "psp", type: "text", value: this.state.psp, disabled: this.inFileExt() != 'pdf', onChange: this.handleChange },
                  this.state.pspFiles.map(function (pspFile) {
                    return React.createElement(
                      "option",
                      { key: pspFile },
                      pspFile
                    );
                  })
                )
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "PDF signature field name: "
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement("input", { id: "psfN", type: "text", value: this.state.psfN, disabled: this.inFileExt() != 'pdf', onChange: this.handleChange })
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "PDF signature field coordinates: "
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement("input", { id: "psfC", type: "text", value: this.state.psfC, disabled: this.inFileExt() != 'pdf', onChange: this.handleChange })
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "Include eID photo as icon in the PDF signature field"
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement("input", { id: "psfP", type: "checkbox", value: this.state.psfP, disabled: this.inFileExt() != 'pdf', onChange: this.handleChange })
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                { colspan: "2" },
                React.createElement("hr", null)
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                { colspan: "2" },
                React.createElement(
                  "b",
                  null,
                  "Non PDF parameters"
                )
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "XSLT file name:"
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement(
                  "select",
                  { id: "xslt", value: this.state.xslt, disabled: this.inFileExt() != 'xml', onChange: this.handleChange },
                  this.state.xsltFiles.map(function (xsltFile) {
                    return React.createElement(
                      "option",
                      { key: xsltFile },
                      xsltFile
                    );
                  })
                )
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "Policy Id:"
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement(
                  "select",
                  { id: "policyId", value: this.state.policyId, disabled: this.inFileExt() == 'pdf', onChange: this.handleChange },
                  React.createElement("option", null),
                  React.createElement(
                    "option",
                    null,
                    "http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/Notary/BE_Justice_Signature_Policy_Notary_eID_Hum_v0.10_202109_Fr.pdf"
                  )
                )
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "Policiy description (Optional):"
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement("input", { id: "policyDescription", type: "text", value: this.state.policyDescription, onChange: this.handleChange, disabled: !this.hasPolicy() || this.inFileExt() == 'pdf' })
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                null,
                React.createElement(
                  "label",
                  null,
                  "Policy Digest Algorithm :"
                )
              ),
              React.createElement(
                "td",
                null,
                React.createElement(
                  "select",
                  { id: "policyDigestAlgorithm", value: this.state.policyDigestAlgorithm, onChange: this.handleChange, disabled: !this.hasPolicy() || this.inFileExt() == 'pdf' },
                  this.policyDigestAlgorithms.map(function (algo) {
                    return React.createElement(
                      "option",
                      { key: algo },
                      algo
                    );
                  })
                )
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                { colspan: "2" },
                React.createElement("hr", null)
              )
            ),
            React.createElement(
              "tr",
              null,
              React.createElement(
                "td",
                { colspan: "2" },
                React.createElement("input", { type: "submit", value: "Submit" })
              )
            )
          )
        )
      );
    }
  }]);

  return NameForm;
}(React.Component);