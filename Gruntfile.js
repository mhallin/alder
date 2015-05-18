module.exports = function(grunt) {
	grunt.initConfig({
		pkg: grunt.file.readJSON('package.json'),
		sass: {
			build: {
				src: 'resources/public/css/style.sass',
				dest: 'resources/public/css/style.css',
			},
		},
		autoprefixer: {
			build: {
				src: 'resources/public/css/style.css',
				dest: 'resources/public/css/style.css',
			},
			options: {
				browsers: ['last 1 version'],
			},
		},
		webpack: {
			audioAll: {
				entry: './resources/public/js/audio/all.js',
				output: {
					path: 'resources/public/js/wp_compiled',
					filename: 'audio_all.js',
				},
			},
		},
		watch: {
			files: ['resources/**/*.sass', 'resources/public/js/audio/**/*.js'],
			tasks: ['default'],
		},
	});

	grunt.loadNpmTasks('grunt-contrib-sass');
	grunt.loadNpmTasks('grunt-contrib-watch');
	grunt.loadNpmTasks('grunt-autoprefixer');
	grunt.loadNpmTasks('grunt-webpack');

	grunt.registerTask('default', ['sass', 'autoprefixer', 'webpack']);

};

