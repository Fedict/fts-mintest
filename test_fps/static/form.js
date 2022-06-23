var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

var NameForm = function (_React$Component) {
    _inherits(NameForm, _React$Component);

    function NameForm(props) {
        var _this$state;

        _classCallCheck(this, NameForm);

        var _this = _possibleConstructorReturn(this, (NameForm.__proto__ || Object.getPrototypeOf(NameForm)).call(this, props));

        _this.getTestFileNames();

        _this.policyDigestAlgorithms = ["SHA1", "SHA224", "SHA256", "SHA384", "SHA512", "SHA3_224", "SHA3_256", "SHA3_384", "SHA3_512", "SHAKE128", "SHAKE256", "SHAKE256_512", "RIPEMD160", "MD2", "MD5", "WHIRLPOOL", "Dummy for out of range test"];

        _this.profilePerType = {
            pdf: ["PADES_1", "PADES_LTA", "PADES_LTA_EXP_ALLOW"],
            xml: ["XADES_1", "XADES_2", "XADES_LT", "XADES_LTA"],
            bin: ["CADES_1", "CADES_2", "CADES_LTA", "CADES_LTA_ENVELOPING"]
        };

        _this.state = (_this$state = {
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
            policyId: ''
        }, _defineProperty(_this$state, "policyId", ''), _defineProperty(_this$state, "policyDescription", 'Policy Description'), _defineProperty(_this$state, "policyDigestAlgorithm", 'SHA512'), _defineProperty(_this$state, "requestDocumentReadConfirm", false), _defineProperty(_this$state, "previewDocuments", true), _defineProperty(_this$state, "profilesForInputType", _this.profilePerType.pdf), _defineProperty(_this$state, "inputFiles", []), _defineProperty(_this$state, "pspFiles", []), _defineProperty(_this$state, "xsltFiles", []), _defineProperty(_this$state, "reasonForNoSubmit", null), _this$state);

        _this.handleSubmit = _this.handleSubmit.bind(_this);
        _this.handleChange = _this.handleChange.bind(_this);
        return _this;
    }

    _createClass(NameForm, [{
        key: "inFileExt",
        value: function inFileExt() {
            if (this.isXadesMultifile()) return "xml";
            return this.state.name[0].toLowerCase().split(".").pop();
        }
    }, {
        key: "isXadesMultifile",
        value: function isXadesMultifile() {
            return this.state.name.length != 1;
        }
    }, {
        key: "handleChange",
        value: function handleChange(event) {
            target = event.target;
            value = target.value;
            if (target.type === 'checkbox') value = target.checked;else if (target.type === 'select-multiple') {
                value = Array.from(target.selectedOptions, function (item) {
                    return item.value;
                });
            }
            this.setState(_defineProperty({}, target.id, value), this.adaptToChanges);
        }
    }, {
        key: "adaptToChanges",
        value: function adaptToChanges() {
            reasonForNoSubmit = null;
            if (!reasonForNoSubmit && this.state.out.length < 5) {
                reasonForNoSubmit = "The output file name must be > 5 character (was : '" + this.state.out + "')";
            }
            if (!reasonForNoSubmit && this.inFileExt() == 'pdf') {
                if (this.state.psfC) {
                    if (!/^[0-9]+,[0-9]+,[0-9]+,[0-9]+,[0-9]+$/.test(this.state.psfC)) {
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
                this.setState({ reasonForNoSubmit: reasonForNoSubmit });
            }

            inFileExt = this.inFileExt();
            bitsOut = this.state.out.toLowerCase().split(".");
            outFileExt = bitsOut.pop();
            if (inFileExt != outFileExt) {
                bitsOut.push(inFileExt);
                this.setState({
                    out: bitsOut.join("."),
                    profilesForInputType: this.profilePerType[inFileExt],
                    prof: this.profilePerType[inFileExt][0]
                });
            }

            this.setState({
                psfN: this.extractAcroformName()
            });
        }
    }, {
        key: "extractAcroformName",
        value: function extractAcroformName() {
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
    }, {
        key: "handleSubmit",
        value: function handleSubmit(event) {
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
            window.location = "sign?" + params;
            event.preventDefault();
        }
    }, {
        key: "serialize",
        value: function serialize(obj) {
            var str = [];
            for (var p in obj) {
                if (obj.hasOwnProperty(p) && obj[p]) {
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

                selectedFilename = inputFiles[0];
                ext = selectedFilename.toLowerCase().split(".").pop();
                bitsOut = _this2.state.out.toLowerCase().split(".");
                bitsOut[bitsOut.length - 1] = ext;
                out = bitsOut.join("."), _this2.setState({
                    pspFiles: pspFiles,
                    psp: pspFiles[0],
                    xsltFiles: xsltFiles,
                    xslt: xsltFiles[0],
                    inputFiles: inputFiles,
                    name: [selectedFilename],
                    profilesForInputType: _this2.profilePerType[ext],
                    prof: _this2.profilePerType[ext][0],
                    out: out,
                    psfN: _this2.extractAcroformName()
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
                                { colSpan: "2" },
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
                                    { id: "name", multiple: true, value: this.state.name, onChange: this.handleChange },
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
                                React.createElement("input", { id: "out", type: "text", value: this.state.out, onChange: this.handleChange })
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
                                    "Sign timeout (in seconds)"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "signTimeout", type: "text", value: this.state.signTimeout, onChange: this.handleChange })
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
                                    "Disable output file download"
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
                                null,
                                React.createElement(
                                    "label",
                                    null,
                                    "Preview documents"
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement("input", { id: "previewDocuments", type: "checkbox", value: this.state.previewDocuments, onChange: this.handleChange })
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                { colSpan: "2" },
                                React.createElement("hr", null)
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                { colSpan: "2" },
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
                                    "Language of the signature (Acroform): "
                                )
                            ),
                            React.createElement(
                                "td",
                                null,
                                React.createElement(
                                    "select",
                                    { id: "lang", value: this.state.lang, onChange: this.handleChange, disabled: this.inFileExt() != 'pdf' },
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
                                { colSpan: "2" },
                                React.createElement("hr", null)
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                { colSpan: "2" },
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
                                    ),
                                    React.createElement(
                                        "option",
                                        null,
                                        "http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/PrivateSeal/BE_Justice_Signature_Policy_PrivateSeal_Hum_v0.5_201512_Nl.pdf"
                                    ),
                                    React.createElement(
                                        "option",
                                        null,
                                        "http://signinfo.eda.just.fgov.be/SignaturePolicy/pdf/PrivateSeal/BE_Justice_Signature_Policy_PrivateSeal_Hum_v0.11_202111_Fr.pdf"
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
                                React.createElement("input", { id: "policyDescription", type: "text", value: this.state.policyDescription, onChange: this.handleChange, disabled: this.inFileExt() == 'pdf' || !this.state.policyId })
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
                                    { id: "policyDigestAlgorithm", value: this.state.policyDigestAlgorithm, onChange: this.handleChange, disabled: this.inFileExt() == 'pdf' || !this.state.policyId },
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
                                { colSpan: "2" },
                                React.createElement("hr", null)
                            )
                        ),
                        React.createElement(
                            "tr",
                            null,
                            React.createElement(
                                "td",
                                { colSpan: "2" },
                                React.createElement("input", { type: "submit", value: "Submit", disabled: this.state.reasonForNoSubmit }),
                                this.state.reasonForNoSubmit && React.createElement(
                                    "p",
                                    null,
                                    React.createElement(
                                        "label",
                                        { style: { color: 'red' } },
                                        "Submit is disabled because : ",
                                        this.state.reasonForNoSubmit
                                    )
                                ),
                                this.isXadesMultifile() && React.createElement(
                                    "p",
                                    null,
                                    React.createElement(
                                        "label",
                                        null,
                                        "This will produce a XADES Multifile signature.",
                                        React.createElement("br", null),
                                        "Policies are allowed, XSLT will be used to produce a custom output XML format"
                                    )
                                )
                            )
                        )
                    )
                )
            );
        }
    }]);

    return NameForm;
}(React.Component);