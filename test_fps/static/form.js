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
            xml: ["XADES_1", "XADES_2", "XADES_LT", "XADES_LTA"],
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
            signTimeout: 120,
            allowedToSign: '',
            policyId: '',
            policyDescription: 'Policy Description',
            policyDigestAlgorithm: 'SHA512',
            requestDocumentReadConfirm: false,

            profilesForInputType: _this.profilePerType.pdf,
            inputFiles: [],
            pspFiles: [],
            xsltFiles: [],
            reasonForNoSubmit: null
        };

        _this.handleSubmit = _this.handleSubmit.bind(_this);
        _this.handleChange = _this.handleChange.bind(_this);
        return _this;
    }

    _createClass(NameForm, [{
        key: "inFileExt",
        value: function inFileExt() {
            bits = this.state.name.toLowerCase().split(".");
            return bits[bits.length - 1];
        }
    }, {
        key: "handleChange",
        value: function handleChange(event) {
            target = event.target;
            this.setState(_defineProperty({}, target.id, target.type === 'checkbox' ? target.checked : target.value), this.adaptToChanges);
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
                this.setState({ reasonForNoSubmit: reasonForNoSubmit });
            }

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
                cleanState.psp = cleanState.psfC = cleanState.psfN = cleanState.psfP = null;
            }

            if (!cleanState.policyId) {
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

                bitsName = inputFiles[0].toLowerCase().split(".");
                ext = bitsName[bitsName.length - 1];
                bitsOut = _this2.state.out.toLowerCase().split(".");
                bitsOut[bitsOut.length - 1] = ext;
                out = bitsOut.join("."), _this2.setState({
                    pspFiles: pspFiles,
                    psp: pspFiles[0],
                    xsltFiles: xsltFiles,
                    xslt: xsltFiles[0],
                    inputFiles: inputFiles,
                    name: inputFiles[0],
                    profilesForInputType: _this2.profilePerType[ext],
                    prof: _this2.profilePerType[ext][0],
                    out: out
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
                                    { id: "name", value: this.state.name, onChange: this.handleChange },
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
                                    "Sign timeout"
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
                                    "label",
                                    null,
                                    React.createElement("br", null),
                                    "Submit is disabled because : ",
                                    this.state.reasonForNoSubmit
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